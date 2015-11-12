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
package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool.ExecCmd;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * Test that the right SshTool is picked up, based on the entity's configuration.
 */
public class EntitySshToolTest extends BrooklynAppUnitTestSupport {

    private SshMachineLocation machine;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        RecordingSshTool.execScriptCmds.clear();
        
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        RecordingSshTool.execScriptCmds.clear();
        super.tearDown();
    }

    @Test
    public void testCustomSshToolClassConfiguredOnEntityWithPrefix() throws Exception {
        MachineEntity entity = app.addChild(EntitySpec.create(MachineEntity.class)
                .configure(BrooklynConfigKeys.SSH_TOOL_CLASS, RecordingSshTool.class.getName()));
        entity.start(ImmutableList.of(machine));
        runCustomSshToolClass(entity);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testCustomSshToolClassConfiguredOnEntityUsingLegacy() throws Exception {
        MachineEntity entity = app.addChild(EntitySpec.create(MachineEntity.class)
                .configure(BrooklynConfigKeys.LEGACY_SSH_TOOL_CLASS, RecordingSshTool.class.getName()));
        entity.start(ImmutableList.of(machine));
        runCustomSshToolClass(entity);
    }
    
    @Test
    public void testCustomSshToolClassConfiguredOnBrooklynProperties() throws Exception {
        mgmt.getBrooklynProperties().put(BrooklynConfigKeys.SSH_TOOL_CLASS, RecordingSshTool.class.getName());
        MachineEntity entity = app.addChild(EntitySpec.create(MachineEntity.class));
        entity.start(ImmutableList.of(machine));
        runCustomSshToolClass(entity);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCustomSshToolClassConfiguredOnBrooklynPropertiesUsingLegaacy() throws Exception {
        mgmt.getBrooklynProperties().put(BrooklynConfigKeys.LEGACY_SSH_TOOL_CLASS, RecordingSshTool.class.getName());
        MachineEntity entity = app.addChild(EntitySpec.create(MachineEntity.class));
        entity.start(ImmutableList.of(machine));
        runCustomSshToolClass(entity);
    }

    protected void runCustomSshToolClass(MachineEntity entity) throws Exception {
        entity.execCommand("myCommand");
        
        boolean found = false;
        for (ExecCmd cmd : RecordingSshTool.execScriptCmds) {
            found = found || cmd.commands.contains("myCommand");
        }
        
        assertTrue(found, "cmds="+RecordingSshTool.execScriptCmds);
    }
}
