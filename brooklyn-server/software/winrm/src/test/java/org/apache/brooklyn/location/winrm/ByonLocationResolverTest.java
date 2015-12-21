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
package org.apache.brooklyn.location.winrm;

import static org.testng.Assert.assertEquals;
import java.util.Map;

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.text.StringPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;

public class ByonLocationResolverTest {

    private static final Logger log = LoggerFactory.getLogger(ByonLocationResolverTest.class);
    
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Predicate<CharSequence> defaultNamePredicate;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        brooklynProperties = managementContext.getBrooklynProperties();
        defaultNamePredicate = StringPredicates.startsWith(FixedListMachineProvisioningLocation.class.getSimpleName());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @DataProvider(name = "windowsOsFamilies")
    public Object[][] getWindowsOsFamilies() {
        return new Object[][]{{"windows"}, {"WINDOWS"}, {"wInDoWs"}};
    }

    @Test(dataProvider = "windowsOsFamilies")
    public void testWindowsMachines(String osFamily) throws Exception {
        brooklynProperties.put("brooklyn.location.byon.user", "myuser");
        brooklynProperties.put("brooklyn.location.byon.password", "mypassword");
        String spec = "byon";
        Map<String, ?> flags = ImmutableMap.of(
                "hosts", ImmutableList.of("1.1.1.1", "2.2.2.2"),
                "osFamily", osFamily
        );
        MachineProvisioningLocation<MachineLocation> provisioner = resolve(spec, flags);
        WinRmMachineLocation location = (WinRmMachineLocation) provisioner.obtain(ImmutableMap.of());

        assertEquals(location.config().get(WinRmMachineLocation.USER), "myuser");
        assertEquals(location.config().get(WinRmMachineLocation.PASSWORD), "mypassword");
        assertEquals(location.config().get(WinRmMachineLocation.ADDRESS).getHostAddress(), "1.1.1.1");
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<MachineLocation> resolve(String val) {
        return (FixedListMachineProvisioningLocation<MachineLocation>) managementContext.getLocationRegistry().resolve(val);
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<MachineLocation> resolve(String val, Map<?, ?> locationFlags) {
        return (FixedListMachineProvisioningLocation<MachineLocation>) managementContext.getLocationRegistry().resolve(val, locationFlags);
    }

}
