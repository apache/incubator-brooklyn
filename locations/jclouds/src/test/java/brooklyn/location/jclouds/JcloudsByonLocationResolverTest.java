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
package brooklyn.location.jclouds;

import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

public class JcloudsByonLocationResolverTest {

    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("jcloudsByon"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:()"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"\")"); // empty hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"i-72b1b132\""); // no closing bracket
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"i-72b1b132\", name)"); // no value for name
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"i-72b1b132\", name=)"); // no value for name
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) managementContext.getLocationRegistry().resolve(spec);
    }
    
    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
}
