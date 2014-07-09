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
package brooklyn.cli;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(suiteName = "ClientWithAuthTest")
public class ClientWithAuthTest extends ClientTest {

  @BeforeSuite(alwaysRun = true)
  public void beforeClass() throws Exception {
    super.oneTimeSetUp("config/test-config-with-auth.yml");
  }

  @AfterSuite(alwaysRun = true)
  public void afterClass() throws Exception {
    super.oneTimeTearDown();
  }

  @Override
  protected void runWithArgs(String... args) throws Exception {
    List<String> argsWithCredentials = Lists.newArrayList(
        "--user", "admin",
        "--password", "admin",
        "--endpoint", "http://localhost:60080"
    );
    argsWithCredentials.addAll(Arrays.asList(args));

    brooklynClient.run(argsWithCredentials.toArray(new String[argsWithCredentials.size()]));
  }
}
