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
package org.apache.brooklyn.entity.machine;

import static org.testng.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.AbstractSoftlayerLiveTest;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SetHostnameCustomizerLiveTest extends BrooklynAppLiveTestSupport {

    public static final String PROVIDER = AbstractSoftlayerLiveTest.PROVIDER;
    public static final String REGION = "ams01";
    public static final String PROVIDER_IMAGE_ID = "CENTOS_6_64";
    public static final String LOCATION_SPEC = PROVIDER + ":" + REGION;
    
    public static final int MAX_TAG_LENGTH = AbstractSoftlayerLiveTest.MAX_TAG_LENGTH;
    public static final int MAX_VM_NAME_LENGTH = AbstractSoftlayerLiveTest.MAX_VM_NAME_LENGTH;

    protected BrooklynProperties brooklynProperties;
    
    protected MachineProvisioningLocation<SshMachineLocation> loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        List<String> propsToRemove = ImmutableList.of("imageId", "imageDescriptionRegex", "imageNameRegex", "inboundPorts", "hardwareId", "minRam");

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String propToRemove : propsToRemove) {
            for (String propVariant : ImmutableList.of(propToRemove, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propToRemove))) {
                brooklynProperties.remove("brooklyn.locations.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.locations."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+propVariant);
            }
        }

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        mgmt = new LocalManagementContext(brooklynProperties);
        
        super.setUp();
        
        loc = (MachineProvisioningLocation<SshMachineLocation>) mgmt.getLocationRegistry().resolve(LOCATION_SPEC);
    }

    @Test(groups = {"Live"})
    public void testSetFixedHostname() throws Exception {
        SetHostnameCustomizer customizer = new SetHostnameCustomizer(ConfigBag.newInstance()
                .configure(SetHostnameCustomizer.FIXED_HOSTNAME, "myhostname"));
        
        MachineEntity entity = app.createAndManageChild(EntitySpec.create(MachineEntity.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.MACHINE_LOCATION_CUSTOMIZERS.getName()), ImmutableSet.of(customizer))
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.IMAGE_ID.getName()), PROVIDER_IMAGE_ID));


        app.start(ImmutableList.of(loc));
        
        SshMachineLocation machine = Machines.findUniqueMachineLocation(entity.getLocations(), SshMachineLocation.class).get();
        
        assertEquals(getHostname(machine), "myhostname");
    }
    
    @Test(groups = {"Live"})
    public void testSetAutogeneratedHostname() throws Exception {
        SetHostnameCustomizer customizer = new SetHostnameCustomizer(ConfigBag.newInstance());

        MachineEntity entity = app.createAndManageChild(EntitySpec.create(MachineEntity.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.MACHINE_LOCATION_CUSTOMIZERS.getName()), ImmutableSet.of(customizer))
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.IMAGE_ID.getName()), "CENTOS_6_64"));


        app.start(ImmutableList.of(loc));
        
        SshMachineLocation machine = Machines.findUniqueMachineLocation(entity.getLocations(), SshMachineLocation.class).get();
        
        String ip;
        if (machine.getPrivateAddresses().isEmpty()) {
            ip = Iterables.get(machine.getPublicAddresses(), 0);
        } else {
            ip = Iterables.get(machine.getPrivateAddresses(), 0);
        }
        String expected = "ip-"+(ip.replace(".", "-")+"-"+machine.getId());
        assertEquals(getHostname(machine), expected);
    }
    
    protected String getHostname(SshMachineLocation machine) {
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        ByteArrayOutputStream errstream = new ByteArrayOutputStream();
        int result = machine.execScript(ImmutableMap.of("out", outstream, "err", errstream), "getHostname", ImmutableList.of("echo hostname=`hostname`"));
        assertEquals(result, 0);
        
        String out = new String(outstream.toByteArray());
        String err = new String(errstream.toByteArray());
        for (String line : out.split("\n")) {
            if (line.contains("hostname=") && !line.contains("`hostname`")) {
                return line.substring(line.indexOf("hostname=") + "hostname=".length()).trim();
            }
        }
        throw new IllegalStateException(String.format("No hostname found for %s (got %s; %s)", machine, out, err));
    }        
}
