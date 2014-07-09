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
package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.testng.Assert;
import org.testng.TestException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.entity.basic.SoftwareProcessEntityTest.MyServiceImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;

public class ScriptHelperTest extends BrooklynAppUnitTestSupport {
    
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
        MyServiceInessentialDriverImpl entity = new MyServiceInessentialDriverImpl(app);
        Entities.manage(entity);
        
        entity.start(ImmutableList.of(loc));
        SimulatedInessentialIsRunningDriver driver = (SimulatedInessentialIsRunningDriver) entity.getDriver();
        Assert.assertTrue(driver.isRunning());
        
        entity.connectServiceUpIsRunning();
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        driver.setFailExecution(true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, false);
        driver.setFailExecution(false);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
    }
    
    private class MyServiceInessentialDriverImpl extends MyServiceImpl {
        public MyServiceInessentialDriverImpl(Entity parent) {
            super(parent);
        }
        
        @Override public Class<?> getDriverInterface() {
            return SimulatedInessentialIsRunningDriver.class;
        }
        
        @Override
        public void connectServiceUpIsRunning() {
            FunctionFeed.builder()
                    .entity(this)
                    .period(500)
                    .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
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
