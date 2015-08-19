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
package org.apache.brooklyn.entity.software.base.test.mysql;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;


public abstract class AbstractToyMySqlEntityTest extends BrooklynAppLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(AbstractToyMySqlEntityTest.class);
    
    protected MachineProvisioningLocation<? extends SshMachineLocation> targetLocation;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        targetLocation = createLocation();
    }

    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(
                LocalhostMachineProvisioningLocation.class));
    }
    
    protected abstract Entity createMysql();

    // deliberately not marked as a test here so that subclasses mark it correctly (Live v Integration)
    public void testMySqlOnProvisioningLocation() throws Exception {
        Entity mysql = createMysql();
        app.start(MutableList.of(targetLocation));
        checkStartsRunning(mysql);
        checkIsRunningAndStops(mysql, (SshMachineLocation) Iterables.getOnlyElement( mysql.getLocations() ));
    }

    protected Integer getPid(Entity mysql) {
        return mysql.getAttribute(Attributes.PID);
    }

    protected void checkStartsRunning(Entity mysql) {
        // should be starting within a few seconds (and almost certainly won't complete in that time)
        EntityTestUtils.assertAttributeEventually(
                mysql, 
                Attributes.SERVICE_STATE_ACTUAL,
                Predicates.or(Predicates.equalTo(Lifecycle.STARTING), Predicates.equalTo(Lifecycle.RUNNING)));
        // should be up and running within 5m 
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", Duration.FIVE_MINUTES),
                mysql, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

    protected void checkIsRunningAndStops(Entity mysql, SshMachineLocation lh) {
        Integer pid = getPid(mysql);
        Assert.assertNotNull(pid, "PID should be set as an attribute (or getPid() overridden to supply)");
        Entities.submit(app, SshEffectorTasks.requirePidRunning(pid).machine(lh).newTask() ).get();
        
        app.stop();

        // let the kill -1 take effect 
        Time.sleep(Duration.ONE_SECOND);
        
        // and assert it has died
        log.info("mysql in pid "+pid+" should be dead now");
        // (app has stopped, so submit on mgmt context)
        ProcessTaskWrapper<Integer> t = SshEffectorTasks.codePidRunning(pid).machine(lh).newTask();
        mgmt.getExecutionManager().submit(t);
        Assert.assertNotEquals(t.block().getExitCode(), (Integer)0);
    }
}
