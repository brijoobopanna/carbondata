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

package org.apache.carbondata.core.index.status;

import java.util.List;

import org.apache.carbondata.core.metadata.schema.table.IndexSchema;

/**
 * It saves/serializes the array of {{@link IndexStatusDetail}} to database folder.
 * It ensures the data consistance while concurrent write through write lock. It saves the status
 * to the indexstatus under the database folder.
 * Now the implement not finished, it used to disable Index in multi-tenant scenario.
 */
public class DatabaseIndexStatusProvider implements IndexStatusStorageProvider {

  @Override
  public IndexStatusDetail[] getIndexStatusDetails() {
    return new IndexStatusDetail[0];
  }

  /**
   * Update or add the status of passed indexes with the given indexstatus. If the indexstatus
   * given is enabled/disabled then updates/adds the index, in case of drop it just removes it
   * from the file.
   * This method always overwrites the old file.
   * @param indexSchemas schemas of which are need to be updated in index status
   * @param indexStatus  status to be updated for the index schemas
   */
  @Override
  public void updateIndexStatus(
      List<IndexSchema> indexSchemas, IndexStatus indexStatus) {
  }
}
