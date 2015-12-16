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
package org.apache.brooklyn.core.mgmt.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.Map;

import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class ExternalConfigSupplierRegistryTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testLooksUpSupplier() throws Exception {
        MyExternalConfigSupplier supplier1 = new MyExternalConfigSupplier(ImmutableMap.of("mykey", "myval1"));
        mgmt.getExternalConfigProviderRegistry().addProvider("myprovider1", supplier1);
        assertEquals(mgmt.getExternalConfigProviderRegistry().getConfig("myprovider1", "mykey"), "myval1");
        assertNull(mgmt.getExternalConfigProviderRegistry().getConfig("myprovider1", "wrongkey"));
        
        MyExternalConfigSupplier supplier2 = new MyExternalConfigSupplier(ImmutableMap.of("mykey", "myval2"));
        mgmt.getExternalConfigProviderRegistry().addProvider("myprovider2", supplier2);
        assertEquals(mgmt.getExternalConfigProviderRegistry().getConfig("myprovider2", "mykey"), "myval2");
    }

    @Test
    public void testExceptionIfSupplierDoesNotExist() throws Exception {
        try {
            assertNull(mgmt.getExternalConfigProviderRegistry().getConfig("wrongprovider", "mykey"));
            fail();
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("No provider found with name")) throw e;
        }
    }

    private static class MyExternalConfigSupplier implements ExternalConfigSupplier {
        private final Map<String, String> conf;
        
        public MyExternalConfigSupplier(Map<String, String> conf) {
            this.conf = conf;
        }
        
        @Override public String getName() {
            return "myprovider";
        }

        @Override public String get(String key) {
            return conf.get(key);
        }
    }
}
