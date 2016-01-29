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
package org.apache.brooklyn.entity.system_service;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessImpl;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.system_service.SystemServiceEnricher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SystemServiceEnricherTest extends BrooklynAppLiveTestSupport {
    //requires /etc/init.d OS, for example CentOS 6.5
    private static final String LOCATION_SPEC = "named:service-live-test-location";
    private JcloudsLocation location;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        location = (JcloudsLocation) mgmt.getLocationRegistry().resolve(LOCATION_SPEC);
    }

    @Test(groups = "Live")
    public void testRestartLaunchesService() {
        String launchCmd = "nohup bash -c \"echo \\$\\$ > $PID_FILE; while true; do sleep 1000; done\" &";
        EntitySpec<VanillaSoftwareProcess> procSpec = EntitySpec.create(VanillaSoftwareProcess.class)
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, launchCmd)
                .enricher(EnricherSpec.create(SystemServiceEnricher.class));
        VanillaSoftwareProcess proc = app.createAndManageChild(procSpec);
        app.start(ImmutableList.of(location));

        waitHealthy(proc);

        SshMachineLocation machine = EffectorTasks.getSshMachine(proc);
        String pidFile = getPidFile(proc);
        String killCmd = "kill -9 `cat " + pidFile + "`";
        machine.execCommands("kill process", ImmutableList.of(killCmd));

        waitFailed(proc);

        int restartCode = machine.execCommands("restart machine", ImmutableList.of(BashCommands.sudo("/sbin/shutdown -r now")));
        assertEquals(restartCode, 0);

        waitHealthy(proc);
    }

    private String getPidFile(VanillaSoftwareProcess proc) {
        VanillaSoftwareProcessImpl impl = (VanillaSoftwareProcessImpl)Entities.deproxy(proc);
        return ((VanillaSoftwareProcessSshDriver)impl.getDriver()).getPidFile();
    }

    private void waitFailed(VanillaSoftwareProcess proc) {
        Asserts.eventually(Suppliers.ofInstance(proc), EntityPredicates.attributeEqualTo(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE), Duration.FIVE_MINUTES);
    }

    private void waitHealthy(VanillaSoftwareProcess proc) {
        Asserts.eventually(Suppliers.ofInstance(proc), EntityPredicates.attributeEqualTo(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING), Duration.FIVE_MINUTES);
    }
}
