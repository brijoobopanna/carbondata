/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.carbondata.mv.rewrite

import java.util.concurrent.locks.ReentrantReadWriteLock

import org.apache.spark.sql.{CarbonToSparkAdapter, DataFrame, SparkSession}
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, SubqueryAlias}
import org.apache.spark.sql.execution.datasources.FindDataSourceTable

import org.apache.carbondata.common.logging.LogServiceFactory
import org.apache.carbondata.core.index.MVCatalog
import org.apache.carbondata.core.index.status.IndexStatusManager
import org.apache.carbondata.core.metadata.schema.table.IndexSchema
import org.apache.carbondata.mv.extension.{MVHelper, MVParser}
import org.apache.carbondata.mv.plans.modular.{Flags, ModularPlan, ModularRelation, Select}
import org.apache.carbondata.mv.plans.util.Signature
import org.apache.carbondata.mv.session.MVSession


/** Holds a summary logical plan */
private[mv] case class SummaryDataset(
    signature: Option[Signature],
    plan: LogicalPlan,
    indexSchema: IndexSchema,
    relation: ModularPlan)

/**
 * It is wrapper on indexSchema relation along with schema.
 */
case class MVPlanWrapper(plan: ModularPlan, indexSchema: IndexSchema) extends ModularPlan {
  override def output: Seq[Attribute] = plan.output

  override def children: Seq[ModularPlan] = plan.children
}

private[mv] class SummaryDatasetCatalog(sparkSession: SparkSession)
  extends MVCatalog[SummaryDataset] {

  private val LOGGER = LogServiceFactory.getLogService(this.getClass.getCanonicalName)

  @transient
  private val summaryDatasets = new scala.collection.mutable.ArrayBuffer[SummaryDataset]

  val mvSession = new MVSession(sparkSession, this)

  @transient
  private val registerLock = new ReentrantReadWriteLock


  /** Acquires a read lock on the catalog for the duration of `f`. */
  private def readLock[A](f: => A): A = {
    val lock = registerLock.readLock()
    lock.lock()
    try f finally {
      lock.unlock()
    }
  }

  /** Acquires a write lock on the catalog for the duration of `f`. */
  private def writeLock[A](f: => A): A = {
    val lock = registerLock.writeLock()
    lock.lock()
    try f finally {
      lock.unlock()
    }
  }

  /** Clears all summary tables. */
  private[mv] def refresh(): Unit = {
    writeLock {
      summaryDatasets.clear()
    }
  }

  /** Checks if the catalog is empty. */
  private[mv] def isEmpty: Boolean = {
    readLock {
      summaryDatasets.isEmpty
    }
  }

  /**
   * Registers the data produced by the logical representation of the given [[DataFrame]]. Unlike
   * `RDD.cache()`, the default storage level is set to be `MEMORY_AND_DISK` because recomputing
   * the in-memory columnar representation of the underlying table is expensive.
   */
  private[mv] def registerSchema(indexSchema: IndexSchema): Unit = {
    writeLock {
      val currentDatabase = sparkSession.catalog.currentDatabase

      // This is required because index schemas are across databases, so while loading the
      // catalog, if the index is in database other than sparkSession.currentDataBase(), then it
      // fails to register, so set the database present in the schema Object
      setCurrentDataBase(indexSchema.getRelationIdentifier.getDatabaseName)
      val mvPlan = try {
        MVParser.getMVPlan(indexSchema.getCtasQuery, sparkSession)
      } catch {
        case ex: Exception =>
          LOGGER.error("Error executing the updated query during register MV schema", ex)
          throw ex
      } finally {
        // here setting back to current database of current session, because if the actual query
        // contains db name in query like, select db1.column1 from table and current database is
        // default and if we drop the db1, still the session has current db as db1.
        // So setting back to current database.
        setCurrentDataBase(currentDatabase)
      }
      val planToRegister = MVHelper.dropDummyFunc(mvPlan)
      val modularPlan =
        mvSession.sessionState.modularizer.modularize(
          mvSession.sessionState.optimizer.execute(planToRegister)).next().semiHarmonized
      val signature = modularPlan.signature
      val identifier = indexSchema.getRelationIdentifier
      val plan = new FindDataSourceTable(sparkSession)
        .apply(sparkSession.sessionState.catalog
          .lookupRelation(TableIdentifier(identifier.getTableName,
            Some(identifier.getDatabaseName))))
      val output = if (plan.isInstanceOf[SubqueryAlias]) {
        CarbonToSparkAdapter.getOutput(plan.asInstanceOf[SubqueryAlias])
      } else {
        plan.output
      }
      val relation = ModularRelation(identifier.getDatabaseName,
        identifier.getTableName,
        output,
        Flags.NoFlags,
        Seq.empty)
      val select = Select(relation.outputList,
        relation.outputList,
        Seq.empty,
        Seq((0, identifier.getTableName)).toMap,
        Seq.empty,
        Seq(relation),
        Flags.NoFlags,
        Seq.empty,
        Seq.empty,
        None)

      summaryDatasets += SummaryDataset(
        signature,
        planToRegister,
        indexSchema,
        MVPlanWrapper(select, indexSchema))
    }
  }

  private def setCurrentDataBase(dataBaseName: String): Unit = {
    sparkSession.catalog.setCurrentDatabase(dataBaseName)
  }

  /** Removes the given [[DataFrame]] from the catalog */
  private[mv] def unregisterSchema(mvName: String): Unit = {
    writeLock {
      val dataIndex = summaryDatasets
        .indexWhere(sd => sd.indexSchema.getIndexName.equals(mvName))
      require(dataIndex >= 0, s"MV $mvName is not registered.")
      summaryDatasets.remove(dataIndex)
    }
  }

  /**
   * Checks if the schema is already registered
   */
  private[mv] def isMVExists(mvName: String): java.lang.Boolean = {
    val dataIndex = summaryDatasets
      .indexWhere(sd => sd.indexSchema.getIndexName.equals(mvName))
    dataIndex > 0
  }

  override def listAllValidSchema(): Array[SummaryDataset] = {
    val statusDetails = IndexStatusManager.getEnabledIndexStatusDetails
    // Only select the enabled indexes for the query.
    val enabledDataSets = summaryDatasets.filter { p =>
      statusDetails.exists(_.getIndexName.equalsIgnoreCase(p.indexSchema.getIndexName))
    }
    enabledDataSets.toArray
  }

  /**
   * API for test only
   *
   * Registers the data produced by the logical representation of the given [[DataFrame]]. Unlike
   * `RDD.cache()`, the default storage level is set to be `MEMORY_AND_DISK` because recomputing
   * the in-memory columnar representation of the underlying table is expensive.
   */
  private[mv] def registerSummaryDataset(
      query: DataFrame,
      tableName: Option[String] = None): Unit = {
    writeLock {
      val planToRegister = query.queryExecution.analyzed
      if (lookupSummaryDataset(planToRegister).nonEmpty) {
        sys.error(s"Asked to register already registered.")
      } else {
        val modularPlan =
          mvSession.sessionState.modularizer.modularize(
            mvSession.sessionState.optimizer.execute(planToRegister)).next().semiHarmonized
        val signature = modularPlan.signature
        summaryDatasets +=
        SummaryDataset(signature, planToRegister, null, null)
      }
    }
  }

  /** Removes the given [[DataFrame]] from the catalog */
  private[mv] def unregisterSummaryDataset(query: DataFrame): Unit = {
    writeLock {
      val planToRegister = query.queryExecution.analyzed
      val dataIndex = summaryDatasets.indexWhere(sd => planToRegister.sameResult(sd.plan))
      require(dataIndex >= 0, s"Table $query is not registered.")
      summaryDatasets.remove(dataIndex)
    }
  }

  /**
   * Check already with same query present in mv
   */
  private[mv] def isMVWithSameQueryPresent(query: LogicalPlan) : Boolean = {
    lookupSummaryDataset(query).nonEmpty
  }

  /**
   * API for test only
   * Tries to remove the data set for the given [[DataFrame]] from the catalog if it's registered
   */
  private[mv] def tryUnregisterSummaryDataset(
      query: DataFrame,
      blocking: Boolean = true): Boolean = {
    writeLock {
      val planToRegister = query.queryExecution.analyzed
      val dataIndex = summaryDatasets.indexWhere(sd => planToRegister.sameResult(sd.plan))
      val found = dataIndex >= 0
      if (found) {
        summaryDatasets.remove(dataIndex)
      }
      found
    }
  }

  /** Optionally returns registered data set for the given [[DataFrame]] */
  private[mv] def lookupSummaryDataset(query: DataFrame): Option[SummaryDataset] = {
    readLock {
      lookupSummaryDataset(query.queryExecution.analyzed)
    }
  }

  /** Returns feasible registered summary data sets for processing the given ModularPlan. */
  private[mv] def lookupSummaryDataset(plan: LogicalPlan): Option[SummaryDataset] = {
    readLock {
      summaryDatasets.find(sd => plan.sameResult(sd.plan))
    }
  }


  /** Returns feasible registered summary data sets for processing the given ModularPlan. */
  private[mv] def lookupFeasibleSummaryDatasets(plan: ModularPlan): Seq[SummaryDataset] = {
    readLock {
      val sig = plan.signature
      val statusDetails = IndexStatusManager.getEnabledIndexStatusDetails
      // Only select the enabled indexes for the query.
      val enabledDataSets = summaryDatasets.filter { p =>
        statusDetails.exists(_.getIndexName.equalsIgnoreCase(p.indexSchema.getIndexName))
      }

      //  ****not sure what enabledDataSets is used for ****
      //  can enable/disable MV move to other place ?
      //    val feasible = enabledDataSets.filter { x =>
      val feasible = enabledDataSets.filter { x =>
        (x.signature, sig) match {
          case (Some(sig1), Some(sig2)) =>
            if (sig1.groupby && sig2.groupby && sig1.datasets.subsetOf(sig2.datasets)) {
              true
            } else if (!sig1.groupby && !sig2.groupby && sig1.datasets.subsetOf(sig2.datasets)) {
              true
            } else {
              false
            }

          case _ => false
        }
      }
      // heuristics: more tables involved in a summary data set => higher query reduction factor
      feasible.sortWith(_.signature.get.datasets.size > _.signature.get.datasets.size)
    }
  }
}
