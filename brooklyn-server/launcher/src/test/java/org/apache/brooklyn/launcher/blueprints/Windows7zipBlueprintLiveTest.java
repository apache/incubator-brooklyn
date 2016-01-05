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
package org.apache.brooklyn.launcher.blueprints;

import java.io.StringReader;

import com.google.common.collect.Iterables;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.software.base.VanillaWindowsProcess;
import org.apache.brooklyn.entity.software.base.test.location.WindowsTestFixture;
import org.apache.brooklyn.location.winrm.AdvertiseWinrmLoginPolicy;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

public class Windows7zipBlueprintLiveTest extends AbstractBlueprintTest {

    private static final Logger LOG = LoggerFactory.getLogger(Windows7zipBlueprintLiveTest.class);

    protected MachineProvisioningLocation<WinRmMachineLocation> location;
    protected WinRmMachineLocation machine;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        location = WindowsTestFixture.setUpWindowsLocation(mgmt);
        machine = location.obtain(ImmutableMap.of());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (location != null && machine != null) location.release(machine);
        } finally {
            super.tearDown();
        }
    }
    
    @Test(groups={"Live"})
    public void test7zip() throws Exception {
        String yamlApp = Joiner.on("\n").join(
                "location:",
                "  byon:",
                "    hosts:",
                "    - winrm: "+machine.getAddress().getHostAddress()+":5985",
                "      password: "+machine.config().get(WinRmMachineLocation.PASSWORD),
                "      user: Administrator",
                "      osFamily: windows",
                "services:",
                "- type: org.apache.brooklyn.windows.7zip:1.0");
        
        Predicate<Application> asserter = new Predicate<Application>() {
            @Override public boolean apply(Application app) {
                VanillaWindowsProcess entity = Iterables.getOnlyElement(Entities.descendants(app, VanillaWindowsProcess.class));
                String winRMAddress = entity.getAttribute(AdvertiseWinrmLoginPolicy.VM_USER_CREDENTIALS); 
                String ipPort = Strings.getFirstWordAfter(winRMAddress, "@");
                String user = Strings.getFirstWord(winRMAddress);
                String password = Strings.getFirstWordAfter(winRMAddress, ":");
                
                WinRmTool winRmTool = WinRmTool.connect(ipPort, user, password);
                WinRmToolResponse winRmResponse = winRmTool.executePs(ImmutableList.of("(Get-Item \"C:\\\\Program Files\\\\7-Zip\\\\7z.exe\").name"));
                
                LOG.info("winRmResponse: code="+winRmResponse.getStatusCode()+"; out="+winRmResponse.getStdOut()+"; err="+winRmResponse.getStdErr());
                return "7z.exe\r\n".equals(winRmResponse.getStdOut());
            }
        };
        
        runCatalogTest("7zip-catalog.yaml", new StringReader(yamlApp), asserter);
    }
}
