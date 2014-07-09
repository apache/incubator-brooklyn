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
package brooklyn.cli.commands;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeployCommandTest {

    DeployCommand cmd;

    @BeforeClass
    public void oneTimeSetUp() {
        cmd = new DeployCommand();
    }

    @AfterClass
    public void oneTimeTearDown(){
        cmd = null;
    }

    @Test(enabled = true)
    public void testInferFormatClass() throws Exception {
        String inferredCommand = cmd.inferAppFormat("brooklyn.cli.MyTestApp");
        assertEquals(inferredCommand, DeployCommand.CLASS_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatGroovy() throws Exception {
        String inferredCommand = cmd.inferAppFormat("/my/path/my.groovy");
        assertEquals(inferredCommand, DeployCommand.GROOVY_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatJson() throws Exception {
        String inferredCommand1 = cmd.inferAppFormat("/my/path/my.json");
        assertEquals(inferredCommand1, DeployCommand.JSON_FORMAT);
        String inferredCommand2 = cmd.inferAppFormat("/my/path/my.jsn");
        assertEquals(inferredCommand2, DeployCommand.JSON_FORMAT);
    }

}
