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
package org.apache.brooklyn.location.ssh;

import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool.ExecCmd;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * Test that the right SshTool is picked up, based on the {@link SshMachineLocation}'s configuration.
 */
public class SshMachineLocationSshToolTest extends BrooklynAppUnitTestSupport {

    // TODO See SshEffectorTasks.getSshFlags, called by AbstractSoftwareProcessSshDriver.getSshFlags.
    // That retrieves all the mgmt.config, entity.config and location.config to search for ssh-related
    // configuration options. If you *just* instantiate the location directly, then it doesn't get the
    // mgmt.config options.
    //
    // See EntitySshToolTest for an equivalent that configures the SshTool on the management context
    // and on the entity.
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        RecordingSshTool.execScriptCmds.clear();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        RecordingSshTool.execScriptCmds.clear();
        super.tearDown();
    }

    @Test
    public void testCustomSshToolClass() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost")
                .configure(SshMachineLocation.SSH_TOOL_CLASS, RecordingSshTool.class.getName()));
        runCustomSshToolClass(machine);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testCustomSshToolClassUsingLegacy() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost")
                .configure(SshTool.PROP_TOOL_CLASS.getName(), RecordingSshTool.class.getName()));
        runCustomSshToolClass(machine);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testCustomSshToolClassPrefersNonLegacy() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost")
                .configure(SshMachineLocation.SSH_TOOL_CLASS.getName(), RecordingSshTool.class.getName())
                .configure(SshTool.PROP_TOOL_CLASS.getName(), "class.does.not.exist"));
        runCustomSshToolClass(machine);
    }
    
    protected void runCustomSshToolClass(SshMachineLocation host2) throws Exception {
        host2.execCommands("mySummary", ImmutableList.of("myCommand"));
        
        boolean found = false;
        for (ExecCmd cmd : RecordingSshTool.execScriptCmds) {
            found = found || cmd.commands.contains("myCommand");
        }
        
        assertTrue(found, "cmds="+RecordingSshTool.execScriptCmds);
    }
}
