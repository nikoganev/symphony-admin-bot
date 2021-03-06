/*
 * Copyright 2017 The Symphony Software Foundation
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package com.symphony.adminbot.util.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by nick.tarsillo on 7/3/17.
 */
public class FileUtil {
  /**
   * Read file in path to string
   * @param path the path to read from
   * @return the file contents as a string
   */
  public static String readFile(String path) throws IOException {
    InputStream is = new FileInputStream(path);
    BufferedReader buf = new BufferedReader(new InputStreamReader(is));
    String line = buf.readLine();
    StringBuilder sb = new StringBuilder();
    while (line != null) {
      sb.append(line).append("\n");
      line = buf.readLine();
    }
    return sb.toString();
  }

  /**
   * ZIPs files in paths to file in output path
   * @param outputPath the path to save ZIP to
   * @param filePaths the paths to zip files
   * @return the zip file
   */
  public static File zipFiles(String outputPath, Set<String> filePaths)
      throws IOException {
    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputPath));
    byte[] buf = new byte[1024];
    for (String path: filePaths) {
      File file = new File(path);
      FileInputStream in = new FileInputStream(file);
      // Add ZIP entry to output stream.
      out.putNextEntry(new ZipEntry(file.getName()));
      // Transfer bytes from the file to the ZIP file
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }

      out.closeEntry();
      in.close();
    }
    out.close();

    return new File(outputPath);
  }

  /**
   * Writes string to file
   * @param value the value to write to file
   * @param path place to save file
   * @throws FileNotFoundException
   * @throws UnsupportedEncodingException
   */
  public static void writeFile(String value, String path)
      throws FileNotFoundException, UnsupportedEncodingException {
    PrintWriter writer = new PrintWriter(path, "UTF-8");
    writer.println(value);
    writer.close();
  }
}
