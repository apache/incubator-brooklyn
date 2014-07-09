/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.commands;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import org.apache.commons.cli.GnuParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.rest.testing.BrooklynRestApiTest;

@Test(singleThreaded = true)
public abstract class BrooklynCommandTest extends BrooklynRestApiTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynCommandTest.class);
    
  private ByteArrayOutputStream outBytes;
  private PrintStream out;

  private ByteArrayOutputStream errBytes;
  private PrintStream err;
  
  @BeforeClass
  @Override
  public void setUpJersey() throws Exception {
    super.setUpJersey();
  }

  @BeforeMethod
  public void setUp() {
    outBytes = new ByteArrayOutputStream();
    out = new PrintStream(outBytes);

    errBytes = new ByteArrayOutputStream();
    err = new PrintStream(errBytes);
  }

  @AfterClass
  @Override
  public void tearDownJersey() throws Exception {
    super.tearDownJersey();
  }

  protected void runCommandWithArgs(Class<? extends BrooklynCommand> clazz, String... args) throws Exception {
    BrooklynCommand cmd = clazz.newInstance();
    cmd.runAsATest(out, err, client(), new GnuParser().parse(cmd.getOptions(), args));
    log.warn("ERROR: "+errBytes.toString());
  }

  protected void runCommandWithArgsSwallowingExceptions(Class<? extends BrooklynCommand> clazz, String... args) throws Exception {
      BrooklynCommand cmd = clazz.newInstance();
      cmd.runAsATest(out, err, client(), new GnuParser().parse(cmd.getOptions(), args));
  }

  protected String standardOut() {
    return outBytes.toString();
  }

  protected String standardErr() {
    return errBytes.toString();
  }

  protected String createTemporaryFileWithContent(String suffix, String content)
      throws IOException {
    File temporaryFile = File.createTempFile("brooklyn-rest", suffix);
    Writer writer = null;

    try {
      writer = new FileWriter(temporaryFile);
      writer.write(content);
      return temporaryFile.getAbsolutePath();

    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }
}
