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

package org.apache.carbondata.sdk.file;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.carbondata.common.exceptions.sql.InvalidLoadOptionException;
import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.metadata.datatype.ArrayType;
import org.apache.carbondata.core.metadata.datatype.DataTypes;
import org.apache.carbondata.core.metadata.datatype.StructField;
import org.apache.carbondata.core.metadata.datatype.StructType;
import org.apache.carbondata.core.util.path.CarbonTablePath;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import scala.Array;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;
import org.apache.avro.Schema;

import static org.apache.hadoop.yarn.webapp.hamlet.HamletSpec.InputType.file;

public class AvroCarbonWriterTest {
  private String path = "./AvroCarbonWriterSuiteWriteFiles";

  @Test
  public void testWriteBasic() throws IOException {
    FileUtils.deleteDirectory(new File(path));

    // Avro schema
    String avroSchema =
        "{" +
            "   \"type\" : \"record\"," +
            "   \"name\" : \"Acme\"," +
            "   \"fields\" : ["
            + "{ \"name\" : \"name\", \"type\" : \"string\" },"
            + "{ \"name\" : \"age\", \"type\" : \"int\" }]" +
        "}";

    String json = "{\"name\":\"bob\", \"age\":10}";

    // conversion to GenericData.Record
    JsonAvroConverter converter = new JsonAvroConverter();
    GenericData.Record record = converter.convertToGenericDataRecord(
        json.getBytes(CharEncoding.UTF_8), new Schema.Parser().parse(avroSchema));

    Field[] fields = new Field[2];
    fields[0] = new Field("name", DataTypes.STRING);
    fields[1] = new Field("age", DataTypes.STRING);

    try {
      CarbonWriter writer = CarbonWriter.builder()
          .withSchema(new org.apache.carbondata.sdk.file.Schema(fields))
          .outputPath(path)
          .isTransactionalTable(true)
          .buildWriterForAvroInput();

      for (int i = 0; i < 100; i++) {
        writer.write(record);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    File segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
    Assert.assertTrue(segmentFolder.exists());

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });
    Assert.assertNotNull(dataFiles);
    Assert.assertEquals(1, dataFiles.length);

    FileUtils.deleteDirectory(new File(path));
  }

  @Test
  public void testWriteAllPrimitive() throws IOException {
    FileUtils.deleteDirectory(new File(path));

    // Avro schema
    // Supported Primitive Datatype.
    // 1. Boolean
    // 2. Int
    // 3. long
    // 4. float -> To carbon Internally it is double.
    // 5. double
    // 6. String

    // Not Supported
    // 1.NULL Datatype
    // 2.Bytes

    String avroSchema = "{\n" + "  \"name\" : \"myrecord\",\n"
        + "  \"namespace\": \"org.apache.parquet.avro\",\n" + "  \"type\" : \"record\",\n"
        + "  \"fields\" : [ "
        + " {\n" + "    \"name\" : \"myboolean\",\n" + "    \"type\" : \"boolean\"\n  },"
        + " {\n" + "    \"name\" : \"myint\",\n" + "    \"type\" : \"int\"\n" + "  }, "
        + " {\n    \"name\" : \"mylong\",\n" + "    \"type\" : \"long\"\n" + "  },"
        + " {\n   \"name\" : \"myfloat\",\n" + "    \"type\" : \"float\"\n" + "  }, "
        + " {\n \"name\" : \"mydouble\",\n" + "    \"type\" : \"double\"\n" + "  },"
        + " {\n \"name\" : \"mystring\",\n" + "    \"type\" : \"string\"\n" + "  }\n" + "] }";

    String json = "{"
        + "\"myboolean\":true, "
        + "\"myint\": 10, "
        + "\"mylong\": 7775656565,"
        + " \"myfloat\": 0.2, "
        + "\"mydouble\": 44.56, "
        + "\"mystring\":\"Ajantha\"}";


    // conversion to GenericData.Record
    JsonAvroConverter converter = new JsonAvroConverter();
    GenericData.Record record = converter.convertToGenericDataRecord(
        json.getBytes(CharEncoding.UTF_8), new Schema.Parser().parse(avroSchema));

    Field[] fields = new Field[6];
    // fields[0] = new Field("mynull", DataTypes.NULL);
    fields[0] = new Field("myboolean", DataTypes.BOOLEAN);
    fields[1] = new Field("myint", DataTypes.INT);
    fields[2] = new Field("mylong", DataTypes.LONG);
    fields[3] = new Field("myfloat", DataTypes.DOUBLE);
    fields[4] = new Field("mydouble", DataTypes.DOUBLE);
    fields[5] = new Field("mystring", DataTypes.STRING);


    try {
      CarbonWriter writer = CarbonWriter.builder()
          .withSchema(new org.apache.carbondata.sdk.file.Schema(fields))
          .outputPath(path)
          .isTransactionalTable(true)
          .buildWriterForAvroInput();

      for (int i = 0; i < 100; i++) {
        writer.write(record);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    File segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
    Assert.assertTrue(segmentFolder.exists());

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });

    Assert.assertNotNull(dataFiles);
    Assert.assertEquals(1, dataFiles.length);

    FileUtils.deleteDirectory(new File(path));
  }


  @Test
  public void testWriteNestedRecord() throws IOException {
    FileUtils.deleteDirectory(new File(path));

    String newAvroSchema =
        "{" +
          " \"type\" : \"record\", " +
          "  \"name\" : \"userInfo\", "  +
          "  \"namespace\" : \"my.example\", " +
          "  \"fields\" : [{\"name\" : \"username\", " +
          "  \"type\" : \"string\", " +
          "  \"default\" : \"NONE\"}, " +

       " {\"name\" : \"age\", " +
       " \"type\" : \"int\", " +
       " \"default\" : -1}, " +

    "{\"name\" : \"address\", " +
     "   \"type\" : { " +
      "  \"type\" : \"record\", " +
       "   \"name\" : \"mailing_address\", " +
        "  \"fields\" : [ {" +
      "        \"name\" : \"street\", " +
       "       \"type\" : \"string\", " +
       "       \"default\" : \"NONE\"}, { " +

      " \"name\" : \"city\", " +
        "  \"type\" : \"string\", " +
        "  \"default\" : \"NONE\"}, " +
         "                 ]}, " +
     " \"default\" : {} " +
   " } " +
"}";

    String mySchema =
    "{" +
    "  \"name\": \"address\", " +
    "   \"type\": \"record\", " +
    "    \"fields\": [  " +
    "  { \"name\": \"name\", \"type\": \"string\"}, " +
    "  { \"name\": \"age\", \"type\": \"int\"}, " +
    "  { " +
    "    \"name\": \"address\", " +
    "      \"type\": { " +
    "    \"type\" : \"record\", " +
    "        \"name\" : \"my_address\", " +
    "        \"fields\" : [ " +
    "    {\"name\": \"street\", \"type\": \"string\"}, " +
    "    {\"name\": \"city\", \"type\": \"string\"} " +
    "  ]} " +
    "  } " +
    "] " +
    "}";

   String json = "{\"name\":\"bob\", \"age\":10, \"address\" : {\"street\":\"abc\", \"city\":\"bang\"}}";


    // conversion to GenericData.Record
    Schema nn = new Schema.Parser().parse(mySchema);
    JsonAvroConverter converter = new JsonAvroConverter();
    GenericData.Record record = converter.convertToGenericDataRecord(
        json.getBytes(CharEncoding.UTF_8), nn);

    Field[] fields = new Field[3];
    fields[0] = new Field("name", DataTypes.STRING);
    fields[1] = new Field("name1", DataTypes.STRING);
    // fields[1] = new Field("age", DataTypes.INT);
    List fld = new ArrayList<StructField>();
    fld.add(new StructField("street", DataTypes.STRING));
    fld.add(new StructField("city", DataTypes.STRING));
    fields[2] = new Field("address", "struct", fld);

    try {
      CarbonWriter writer = CarbonWriter.builder()
          .withSchema(new org.apache.carbondata.sdk.file.Schema(fields))
          .outputPath(path)
          .isTransactionalTable(true)
          .buildWriterForAvroInput();

      for (int i = 0; i < 100; i++) {
        writer.write(record);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    File segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
    Assert.assertTrue(segmentFolder.exists());

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });
    Assert.assertNotNull(dataFiles);
    Assert.assertEquals(1, dataFiles.length);

    FileUtils.deleteDirectory(new File(path));
  }


  @Test
  public void testWriteNestedRecordWithMeasure() throws IOException {
    FileUtils.deleteDirectory(new File(path));

    String mySchema =
        "{" +
            "  \"name\": \"address\", " +
            "   \"type\": \"record\", " +
            "    \"fields\": [  " +
            "  { \"name\": \"name\", \"type\": \"string\"}, " +
            "  { \"name\": \"age\", \"type\": \"int\"}, " +
            "  { " +
            "    \"name\": \"address\", " +
            "      \"type\": { " +
            "    \"type\" : \"record\", " +
            "        \"name\" : \"my_address\", " +
            "        \"fields\" : [ " +
            "    {\"name\": \"street\", \"type\": \"string\"}, " +
            "    {\"name\": \"city\", \"type\": \"string\"} " +
            "  ]} " +
            "  } " +
            "] " +
            "}";

    String json = "{\"name\":\"bob\", \"age\":10, \"address\" : {\"street\":\"abc\", \"city\":\"bang\"}}";


    // conversion to GenericData.Record
    Schema nn = new Schema.Parser().parse(mySchema);
    JsonAvroConverter converter = new JsonAvroConverter();
    GenericData.Record record = converter.convertToGenericDataRecord(
        json.getBytes(CharEncoding.UTF_8), nn);

    Field[] fields = new Field[3];
    fields[0] = new Field("name", DataTypes.STRING);
    fields[1] = new Field("name1", DataTypes.STRING);
    // fields[1] = new Field("age", DataTypes.INT);
    List fld = new ArrayList<StructField>();
    fld.add(new StructField("street", DataTypes.STRING));
    fld.add(new StructField("city", DataTypes.STRING));
    fields[2] = new Field("address", "struct", fld);

    try {
      CarbonWriter writer = CarbonWriter.builder()
          .withSchema(new org.apache.carbondata.sdk.file.Schema(fields))
          .outputPath(path)
          .isTransactionalTable(true)
          .buildWriterForAvroInput();

      for (int i = 0; i < 100; i++) {
        writer.write(record);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    File segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
    Assert.assertTrue(segmentFolder.exists());

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });
    Assert.assertNotNull(dataFiles);
    Assert.assertEquals(1, dataFiles.length);

    FileUtils.deleteDirectory(new File(path));
  }


  private void WriteAvroComplexData(String mySchema, String json, String[] sortColumns)
      throws UnsupportedEncodingException, IOException, InvalidLoadOptionException {
    Field[] fields = new Field[4];
    fields[0] = new Field("name", DataTypes.STRING);
    fields[1] = new Field("name1", DataTypes.STRING);
    // fields[1] = new Field("age", DataTypes.INT);
    List fld = new ArrayList<StructField>();
    fld.add(new StructField("street", DataTypes.STRING));
    fld.add(new StructField("city", DataTypes.STRING));
    fields[2] = new Field("address", "struct", fld);
    List fld1 = new ArrayList<StructField>();
    fld1.add(new StructField("eachDoorNum", DataTypes.INT));
    fields[3] = new Field("doorNum","array",fld1);

    // conversion to GenericData.Record
    Schema nn = new Schema.Parser().parse(mySchema);
    JsonAvroConverter converter = new JsonAvroConverter();
    GenericData.Record record = converter.convertToGenericDataRecord(
        json.getBytes(CharEncoding.UTF_8), nn);

    try {
      CarbonWriter writer = CarbonWriter.builder()
          .withSchema(new org.apache.carbondata.sdk.file.Schema(fields))
          .outputPath(path)
          .isTransactionalTable(true).sortBy(sortColumns)
          .buildWriterForAvroInput();

      for (int i = 0; i < 100; i++) {
        writer.write(record);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }


  @Test
  public void testWriteComplexRecord() throws IOException, InvalidLoadOptionException {
    FileUtils.deleteDirectory(new File(path));

    String mySchema =
        "{" +
            "  \"name\": \"address\", " +
            "   \"type\": \"record\", " +
            "    \"fields\": [  " +
            "  { \"name\": \"name\", \"type\": \"string\"}, " +
            "  { \"name\": \"age\", \"type\": \"int\"}, " +
            "  { " +
            "    \"name\": \"address\", " +
            "      \"type\": { " +
            "    \"type\" : \"record\", " +
            "        \"name\" : \"my_address\", " +
            "        \"fields\" : [ " +
            "    {\"name\": \"street\", \"type\": \"string\"}, " +
            "    {\"name\": \"city\", \"type\": \"string\"} " +
            "  ]} " +
            "  }, " +
            "  {\"name\" :\"doorNum\", " +
            "   \"type\" : { " +
            "   \"type\" :\"array\", " +
            "   \"items\":{ " +
            "   \"name\" :\"EachdoorNums\", " +
            "   \"type\" : \"int\", " +
            "   \"default\":-1} " +
            "              } " +
            "  }] " +
            "}";

    String json = "{\"name\":\"bob\", \"age\":10, \"address\" : {\"street\":\"abc\", \"city\":\"bang\"}, "
        + "   \"doorNum\" : [1,2,3,4]}";

    WriteAvroComplexData(mySchema, json, null);

    File segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
    Assert.assertTrue(segmentFolder.exists());

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });
    Assert.assertNotNull(dataFiles);
    Assert.assertEquals(1, dataFiles.length);

    FileUtils.deleteDirectory(new File(path));
  }


  @Test
  public void testWriteComplexRecordWithSortColumns() throws IOException {
    FileUtils.deleteDirectory(new File(path));

    String mySchema =
        "{" +
            "  \"name\": \"address\", " +
            "   \"type\": \"record\", " +
            "    \"fields\": [  " +
            "  { \"name\": \"name\", \"type\": \"string\"}, " +
            "  { \"name\": \"age\", \"type\": \"int\"}, " +
            "  { " +
            "    \"name\": \"address\", " +
            "      \"type\": { " +
            "    \"type\" : \"record\", " +
            "        \"name\" : \"my_address\", " +
            "        \"fields\" : [ " +
            "    {\"name\": \"street\", \"type\": \"string\"}, " +
            "    {\"name\": \"city\", \"type\": \"string\"} " +
            "  ]} " +
            "  }, " +
            "  {\"name\" :\"doorNum\", " +
            "   \"type\" : { " +
            "   \"type\" :\"array\", " +
            "   \"items\":{ " +
            "   \"name\" :\"EachdoorNums\", " +
            "   \"type\" : \"int\", " +
            "   \"default\":-1} " +
            "              } " +
            "  }] " +
            "}";

    String json = "{\"name\":\"bob\", \"age\":10, \"address\" : {\"street\":\"abc\", \"city\":\"bang\"}, "
        + "   \"doorNum\" : [1,2,3,4]}";

    try {
      WriteAvroComplexData(mySchema, json, new String[] { "doorNum" });
      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(true);
    }
    FileUtils.deleteDirectory(new File(path));
  }



}