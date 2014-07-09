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
package brooklyn.location.jclouds.provider;

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.jclouds.JcloudsLocation
import brooklyn.location.jclouds.JcloudsSshMachineLocation
import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.collections.MutableMap

import com.google.common.collect.ImmutableList

/**
 * Tests vcloud, with Carrenza. Uses the cloudsoft test account (hard-coding its NAT Mapping, 
 * and one of its private vApp templates). Note that the template is for a Windows 2008 
 * machine with winsshd installed.
 * 
 * TODO Will only work with >= jclouds 1.5, due to jclouds issues 994 and 995. Therefore it 
 * will not work in brooklyn 0.4.0-M2 etc.
 */
class CarrenzaLocationLiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(CarrenzaLocationLiveTest.class)
    
    private static final String PROVIDER = "vcloud"
    private static final String ENDPOINT = "https://myvdc.carrenza.net/api"
    private static final String LOCATION_ID = "jclouds:"+PROVIDER+":"+ENDPOINT;
    private static final String WINDOWS_IMAGE_ID = "https://myvdc.carrenza.net/api/v1.0/vAppTemplate/vappTemplate-2bd5b0ff-ecd9-405e-8306-2f4f6c092a1b"
    
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private JcloudsLocation loc;
    private Collection<SshMachineLocation> machines = []
    
    // TODO Has not been tested since updating ot remove use of deleted LocationRegistry!
    @BeforeMethod(groups = "Live")
    public void setUp() {
        System.out.println("classpath="+System.getProperty("java.class.path"));
        
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-description-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-name-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-id");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".inboundPorts");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".hardware-id");

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".jclouds.endpoint", ENDPOINT)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".imageId", WINDOWS_IMAGE_ID)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".noDefaultSshKeys", true)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".userName", "Administrator")
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".dontCreateUser", true)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".overrideLoginUser", "Administrator")
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".waitForSshable", false)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".runAsRoot", false)
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".inboundPorts", [22, 3389])
        brooklynProperties.put("brooklyn.jclouds."+PROVIDER+".natMapping", [("192.168.0.100"):"195.3.186.200", ("192.168.0.101"):"195.3.186.42"])

        managementContext = new LocalManagementContext(brooklynProperties);
        loc = (JcloudsLocation) managementContext.getLocationRegistry().resolve(LOCATION_ID);
    }
    
    @AfterMethod(groups = "Live")
    public void tearDown() {
        List<Exception> exceptions = []
        machines.each {
            try {
                loc?.release(it)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $it; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
        machines.clear()
    }
    
    // FIXME Disabled because of jclouds issues #994 and #995 (fixed in jclouds 1.5, so not in brooklyn 0.4.0-M2 etc)
    // Note the careful settings in setUp (e.g. so don't try to install ssh-keys etc
    // Also, the windows image used has winsshd installed
    @Test(enabled=false, groups = [ "Live" ])
    public void testProvisionWindowsVm() {
        JcloudsSshMachineLocation machine = obtainMachine(MutableMap.of(
                "imageId", WINDOWS_IMAGE_ID));
        
        LOG.info("Provisioned Windows VM {}; checking if has password", machine)
        String password = machine.waitForPassword();
        assertNotNull(password);
        
        LOG.info("Checking can ssh to windows machine {} using password {}", machine, password);
        assertEquals(machine.execCommands(MutableMap.of("password", password), "check-reachable", ImmutableList.of("hostname")), 0);
    }
    
    // Use this utility method to ensure machines are released on tearDown
    protected SshMachineLocation obtainMachine(Map flags) {
        SshMachineLocation result = loc.obtain(flags)
        machines.add(result)
        return result
    }
    
    protected SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine)
        loc.release(machine)
    }
}
