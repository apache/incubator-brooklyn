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
package brooklyn.entity.database.mysql;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.SoftwareProcess.RestartSoftwareParameters;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.CollectionFunctionals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests restart of the software *process* (as opposed to the VM).
 */
public class MySqlRestartIntegrationTest extends BrooklynAppLiveTestSupport {
    
    // TODO Remove duplication from TomcatServerRestartIntegrationTest
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MySqlRestartIntegrationTest.class);

    @Test(groups="Integration")
    public void testStopProcessAndRestart() throws Exception {
        runStopProcessAndRestart(
                MySqlNode.RESTART, 
                ImmutableMap.of(RestartSoftwareParameters.RESTART_MACHINE.getName(), RestartSoftwareParameters.RestartMachineMode.FALSE));
    }
    
    @Test(groups="Integration")
    public void testStopProcessAndStart() throws Exception {
        runStopProcessAndRestart(
                MySqlNode.START, 
                ImmutableMap.of("locations", ImmutableList.of()));
    }
    
    protected void runStopProcessAndRestart(Effector<?> restartEffector, Map<String, ?> args) throws Exception {
        LocalhostMachineProvisioningLocation loc = app.newLocalhostProvisioningLocation();
        MySqlNode entity = app.createAndManageChild(EntitySpec.create(MySqlNode.class));
        app.start(ImmutableList.of(loc));
        
        Entities.invokeEffector(app, entity, MySqlNode.STOP, ImmutableMap.of(
                StopSoftwareParameters.STOP_MACHINE.getName(), false))
                .get();
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_PROCESS_IS_RUNNING, false);
        EntityTestUtils.assertAttributeEventually(entity, ServiceStateLogic.SERVICE_NOT_UP_INDICATORS, CollectionFunctionals.<String>mapSizeEquals(1));
        
        Entities.invokeEffector(app, entity, restartEffector, args).get();
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MySqlNode.SERVICE_PROCESS_IS_RUNNING, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, ServiceStateLogic.SERVICE_NOT_UP_INDICATORS, ImmutableMap.<String, Object>of());

        EntityTestUtils.assertAttributeEqualsEventually(app, MySqlNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(app, MySqlNode.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
}
