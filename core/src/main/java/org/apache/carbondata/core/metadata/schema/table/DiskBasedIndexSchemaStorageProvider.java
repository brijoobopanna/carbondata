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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.carbondata.common.exceptions.sql.NoSuchIndexException;
import org.apache.carbondata.common.logging.LogServiceFactory;
import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.datastore.filesystem.CarbonFile;
import org.apache.carbondata.core.datastore.filesystem.CarbonFileFilter;
import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.util.CarbonUtil;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.log4j.Logger;

/**
 * Stores index schema in disk as json format
 */
public class DiskBasedIndexSchemaStorageProvider implements IndexSchemaStorageProvider {

  private Logger LOG = LogServiceFactory.getLogService(this.getClass().getCanonicalName());

  private String storePath;

  private String mdtFilePath;

  private long lastModifiedTime;

  private Set<IndexSchema> indexSchemas = new HashSet<>();

  public DiskBasedIndexSchemaStorageProvider(String storePath) {
    this.storePath = CarbonUtil.checkAndAppendHDFSUrl(storePath);
    this.mdtFilePath = storePath + CarbonCommonConstants.FILE_SEPARATOR + "index.mdtfile";
  }

  @Override
  public void saveSchema(IndexSchema indexSchema) throws IOException {
    BufferedWriter brWriter = null;
    DataOutputStream dataOutputStream = null;
    Gson gsonObjectToWrite = new Gson();
    String schemaPath = getSchemaPath(storePath, indexSchema.getIndexName());
    if (FileFactory.isFileExist(schemaPath)) {
      throw new IOException(
              "Index with name " + indexSchema.getIndexName() + " already exists in storage");
    }
    // write the index schema in json format.
    try {
      FileFactory.mkdirs(storePath);
      FileFactory.createNewFile(schemaPath);
      dataOutputStream =
          FileFactory.getDataOutputStream(schemaPath);
      brWriter = new BufferedWriter(new OutputStreamWriter(dataOutputStream,
          Charset.forName(CarbonCommonConstants.DEFAULT_CHARSET)));

      String metadataInstance = gsonObjectToWrite.toJson(indexSchema);
      brWriter.write(metadataInstance);
    } finally {
      if (null != brWriter) {
        brWriter.flush();
      }
      indexSchemas.add(indexSchema);
      CarbonUtil.closeStreams(dataOutputStream, brWriter);
      checkAndReloadIndexSchemas(true);
      touchMDTFile();
    }
  }

  @Override
  public IndexSchema retrieveSchema(String indexName) throws IOException, NoSuchIndexException {
    checkAndReloadIndexSchemas(true);
    for (IndexSchema indexSchema : indexSchemas) {
      if (indexSchema.getIndexName().equalsIgnoreCase(indexName)) {
        return indexSchema;
      }
    }
    throw new NoSuchIndexException(indexName);
  }

  @Override
  public List<IndexSchema> retrieveSchemas(CarbonTable carbonTable) throws IOException {
    checkAndReloadIndexSchemas(false);
    List<IndexSchema> indexSchemas = new ArrayList<>();
    for (IndexSchema indexSchema : this.indexSchemas) {
      List<RelationIdentifier> parentTables = indexSchema.getParentTables();
      for (RelationIdentifier identifier : parentTables) {
        if (StringUtils.isNotEmpty(identifier.getTableId())) {
          if (identifier.getTableId().equalsIgnoreCase(carbonTable.getTableId())) {
            indexSchemas.add(indexSchema);
            break;
          }
        } else if (identifier.getTableName().equalsIgnoreCase(carbonTable.getTableName()) &&
            identifier.getDatabaseName().equalsIgnoreCase(carbonTable.getDatabaseName())) {
          indexSchemas.add(indexSchema);
          break;
        }
      }
    }
    return indexSchemas;
  }

  @Override
  public List<IndexSchema> retrieveAllSchemas() throws IOException {
    checkAndReloadIndexSchemas(true);
    return new ArrayList<>(indexSchemas);
  }

  private Set<IndexSchema> retrieveAllSchemasInternal() throws IOException {
    Set<IndexSchema> indexSchemas = new HashSet<>();
    CarbonFile carbonFile = FileFactory.getCarbonFile(storePath);
    CarbonFile[] carbonFiles = carbonFile.listFiles(new CarbonFileFilter() {
      @Override
      public boolean accept(CarbonFile file) {
        return file.getName().endsWith(".dmschema");
      }
    });

    for (CarbonFile file :carbonFiles) {
      Gson gsonObjectToRead = new Gson();
      DataInputStream dataInputStream = null;
      BufferedReader buffReader = null;
      InputStreamReader inStream = null;
      try {
        String absolutePath = file.getAbsolutePath();
        dataInputStream =
            FileFactory.getDataInputStream(
                absolutePath);
        inStream = new InputStreamReader(dataInputStream,
            Charset.forName(CarbonCommonConstants.DEFAULT_CHARSET));
        buffReader = new BufferedReader(inStream);
        indexSchemas.add(gsonObjectToRead.fromJson(buffReader, IndexSchema.class));
      } finally {
        CarbonUtil.closeStreams(buffReader, inStream, dataInputStream);
      }
    }
    return indexSchemas;
  }

  @Override
  public void dropSchema(String indexName)
      throws IOException {
    String schemaPath = getSchemaPath(storePath, indexName);
    if (!FileFactory.isFileExist(schemaPath)) {
      throw new IOException("Index with name " + indexName + " does not exists in storage");
    }

    LOG.info(String.format("Trying to delete Index %s schema", indexName));

    indexSchemas.removeIf(schema -> schema.getIndexName().equalsIgnoreCase(indexName));
    touchMDTFile();
    if (!FileFactory.deleteFile(schemaPath)) {
      throw new IOException("Index with name " + indexName + " cannot be deleted");
    }
    LOG.info(String.format("Index %s schema is deleted", indexName));
  }

  private void checkAndReloadIndexSchemas(boolean touchFile) throws IOException {
    if (FileFactory.isFileExist(mdtFilePath)) {
      long lastModifiedTime = FileFactory.getCarbonFile(mdtFilePath).getLastModifiedTime();
      if (this.lastModifiedTime != lastModifiedTime) {
        indexSchemas = retrieveAllSchemasInternal();
        touchMDTFile();
      }
    } else {
      indexSchemas = retrieveAllSchemasInternal();
      if (touchFile) {
        touchMDTFile();
      }
    }
  }

  private void touchMDTFile() throws IOException {
    if (!FileFactory.isFileExist(storePath)) {
      FileFactory.createDirectoryAndSetPermission(
          storePath,
          new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.ALL));
    }
    if (!FileFactory.isFileExist(mdtFilePath)) {
      FileFactory.createNewFile(
          mdtFilePath,
          new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.ALL));
    }
    long lastModifiedTime = System.currentTimeMillis();
    FileFactory.getCarbonFile(mdtFilePath).setLastModifiedTime(lastModifiedTime);
    this.lastModifiedTime = lastModifiedTime;
  }

  /**
   * it returns the schema path for the index
   * @param storePath
   * @param indexName
   * @return
   */
  public static String getSchemaPath(String storePath, String indexName) {
    String schemaPath =  storePath + CarbonCommonConstants.FILE_SEPARATOR + indexName
        + ".dmschema";
    return schemaPath;
  }
}
