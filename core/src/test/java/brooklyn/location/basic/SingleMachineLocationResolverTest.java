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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Map;
import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableMap;

public class SingleMachineLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext(BrooklynProperties.Factory.newEmpty());
        brooklynProperties = managementContext.getBrooklynProperties();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("single");
    }
    
    @Test
    public void testThrowsOnInvalidTarget() throws Exception {
        assertThrowsIllegalArgument("single:()");
        assertThrowsIllegalArgument("single:(wrongprefix:(hosts=\"1.1.1.1\"))");
        assertThrowsIllegalArgument("single:(foo:bar)");
    }

    @Test
    public void resolveHosts() {
        resolve("single:(target=localhost)");
        resolve("single:(target=named:foo)");
        resolve("single:(target=byon:(hosts=\"1.1.1.1\"))");
    }
    
    @Test
    public void testNamedByonLocation() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "single:(target=byon:(hosts=\"1.1.1.1\"))");
        
        SingleMachineProvisioningLocation<SshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain(ImmutableMap.of()).getAddress(), InetAddress.getByName("1.1.1.1"));
    }

    @Test
    public void testPropertyScopePrescedence() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "single:(target=byon:(hosts=\"1.1.1.1\"))");
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = resolve("named:mynamed").obtain(ImmutableMap.of()).getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
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
    
    @SuppressWarnings("unchecked")
    private SingleMachineProvisioningLocation<SshMachineLocation> resolve(String val) {
        return (SingleMachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(val);
    }
    
}
