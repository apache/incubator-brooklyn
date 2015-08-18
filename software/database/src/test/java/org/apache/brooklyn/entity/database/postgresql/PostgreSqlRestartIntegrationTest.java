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
package org.apache.brooklyn.entity.database.postgresql;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractSoftwareProcessRestartIntegrationTest;
import brooklyn.entity.basic.SoftwareProcess;

/**
 * Tests restart of the software *process* (as opposed to the VM).
 */
@Test(groups="Integration")
public class PostgreSqlRestartIntegrationTest extends AbstractSoftwareProcessRestartIntegrationTest {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlRestartIntegrationTest.class);

    @Override
    protected EntitySpec<? extends SoftwareProcess> newEntitySpec() {
        return EntitySpec.create(PostgreSqlNode.class);
    }
    
    // TODO The second start() will fail because customize operations forbidden while there is existing data:
    //      "If you want to create a new database system, either remove or empty".
    // I haven't checked whether it damaged the data in the database though!
    @Test(enabled=false, groups={"Integration", "WIP"})
    public void testStopProcessAndStart() throws Exception {
        super.testStopProcessAndStart();
    }
}
