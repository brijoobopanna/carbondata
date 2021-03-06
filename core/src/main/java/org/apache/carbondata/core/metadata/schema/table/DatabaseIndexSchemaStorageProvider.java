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

package org.apache.carbondata.core.metadata.schema.table;

import java.util.ArrayList;
import java.util.List;

import org.apache.carbondata.common.exceptions.sql.NoSuchIndexException;

/**
 * Stores index schema in database
 */
public class DatabaseIndexSchemaStorageProvider implements IndexSchemaStorageProvider {

  public DatabaseIndexSchemaStorageProvider() {
  }

  @Override
  public void saveSchema(IndexSchema indexSchema) {
    throw new UnsupportedOperationException("not support saving Index schema into database");
  }

  @Override
  public IndexSchema retrieveSchema(String indexName) throws NoSuchIndexException {
    throw new NoSuchIndexException(indexName);
  }

  @Override
  public List<IndexSchema> retrieveSchemas(CarbonTable carbonTable) {
    return new ArrayList<>(0);
  }

  @Override
  public List<IndexSchema> retrieveAllSchemas() {
    return new ArrayList<>(0);
  }

  @Override
  public void dropSchema(String indexName) {
    throw new UnsupportedOperationException("not support dropping Index schema from database");
  }
}
