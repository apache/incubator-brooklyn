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
package org.apache.brooklyn.entity.software.base.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest;
import org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest.MyService;
import org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest.MyServiceImpl;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.TestException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;

public class ScriptHelperTest extends BrooklynAppUnitTestSupport {
    
    private static final Logger log = LoggerFactory.getLogger(ScriptHelperTest.class);
    
    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    boolean shouldFail = false;
    int failCount = 0;
    
    @BeforeMethod(alwaysRun=true)
    @SuppressWarnings("unchecked")
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class));
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        loc.addMachine(machine);
    }

    @Test
    public void testCheckRunningForcesInessential() {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class, MyServiceInessentialDriverImpl.class));
        
        entity.start(ImmutableList.of(loc));
        SimulatedInessentialIsRunningDriver driver = (SimulatedInessentialIsRunningDriver) entity.getDriver();
        Assert.assertTrue(driver.isRunning());
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, SoftwareProcess.SERVICE_PROCESS_IS_RUNNING, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        
        log.debug("up, now cause failure");
        
        driver.setFailExecution(true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, SoftwareProcess.SERVICE_PROCESS_IS_RUNNING, false);
        
        log.debug("caught failure, now clear");
        driver.setFailExecution(false);
        EntityTestUtils.assertAttributeEqualsEventually(entity, SoftwareProcess.SERVICE_PROCESS_IS_RUNNING, true);
    }
    
    public static class MyServiceInessentialDriverImpl extends MyServiceImpl {

        @Override public Class<?> getDriverInterface() {
            return SimulatedInessentialIsRunningDriver.class;
        }

        @Override
        protected void connectSensors() {
            super.connectSensors();
            connectServiceUpIsRunning();
        }
        
        @Override
        public void connectServiceUpIsRunning() {
//            super.connectServiceUpIsRunning();
            // run more often
            FunctionFeed.builder()
                .entity(this)
                .period(Duration.millis(10))
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_PROCESS_IS_RUNNING)
                    .onException(Functions.constant(Boolean.FALSE))
                    .callable(new Callable<Boolean>() {
                        public Boolean call() {
                            return getDriver().isRunning();
                        }
                    }))
                .build();
        }
    }
    
    public static class SimulatedInessentialIsRunningDriver extends SoftwareProcessEntityTest.SimulatedDriver {
        private boolean failExecution = false;

        public SimulatedInessentialIsRunningDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public boolean isRunning() {
            return newScript(CHECK_RUNNING)
                .execute() == 0;
        }
        
        @Override
        public int execute(List<String> script, String summaryForLogging) {
            if (failExecution) {
                throw new TestException("Simulated driver exception");
            }
            return 0;
        }
        
        @SuppressWarnings("rawtypes")
        @Override
        public int execute(Map flags2, List<String> script, String summaryForLogging) {
            if (failExecution) {
                throw new TestException("Simulated driver exception");
            }
            return 0;
        }
        
        public void setFailExecution(boolean failExecution) {
            this.failExecution = failExecution;
        }
        
    }
    
}
