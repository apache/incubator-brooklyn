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
package brooklyn.location.basic;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.AbstractGoogleComputeLiveTest;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.MachineDetails;
import brooklyn.location.OsDetails;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

// This test really belongs in brooklyn-location but depends on AbstractGoogleComputeLiveTest in brooklyn-software-base
public class MachineDetailsGoogleComputeLiveTest extends AbstractGoogleComputeLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(MachineDetailsGoogleComputeLiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        Entity testEntity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(testEntity, Startable.SERVICE_UP, true);

        SshMachineLocation sshLoc = Locations.findUniqueSshMachineLocation(testEntity.getLocations()).get();
        MachineDetails machine = app.getExecutionContext()
                .submit(BasicMachineDetails.taskForSshMachineLocation(sshLoc))
                .getUnchecked();
        LOG.info("Found the following at {}: {}", loc, machine);
        assertNotNull(machine);
        OsDetails details = machine.getOsDetails();
        assertNotNull(details);
        assertNotNull(details.getArch());
        assertNotNull(details.getName());
        assertNotNull(details.getVersion());
        assertFalse(details.getArch().startsWith("architecture:"));
        assertFalse(details.getName().startsWith("name:"));
        assertFalse(details.getVersion().startsWith("version:"));
    }
}
