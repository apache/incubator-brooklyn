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

import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;

public class LocationPropertiesFromBrooklynPropertiesTest {

    private LocationPropertiesFromBrooklynProperties parser;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        parser = new LocationPropertiesFromBrooklynProperties();
    }
    
    @Test
    public void testExtractProviderProperties() throws Exception {
        String provider = "myprovider";
        String namedLocation = null;
        
        Map<String, String> properties = Maps.newLinkedHashMap();
        
        // prefer those in "named" over everything else
        properties.put("brooklyn.location.myprovider.privateKeyFile", "privateKeyFile-inProviderSpecific");
        properties.put("brooklyn.location.privateKeyFile", "privateKeyFile-inLocationGeneric");

        // prefer location-generic if nothing else
        properties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inLocationGeneric");
        
        Map<String, Object> conf = parser.getLocationProperties(provider, namedLocation, properties);
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inProviderSpecific");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inLocationGeneric");
    }

    @Test
    public void testExtractNamedLocationProperties() throws Exception {
        String provider = "myprovider";
        String namedLocation = "mynamed";
        
        Map<String, String> properties = Maps.newLinkedHashMap();
        
        properties.put("brooklyn.location.named.mynamed", "myprovider");
        
        // prefer those in "named" over everything else
        properties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        properties.put("brooklyn.location.myprovider.privateKeyFile", "privateKeyFile-inProviderSpecific");
        properties.put("brooklyn.location.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer those in provider-specific over generic
        properties.put("brooklyn.location.myprovider.publicKeyFile", "publicKeyFile-inProviderSpecific");
        properties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inGeneric");

        // prefer location-generic if nothing else
        properties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = parser.getLocationProperties(provider, namedLocation, properties);
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
    }

    @Test
    public void testConvertsDeprecatedFormats() throws Exception {
        String provider = "myprovider";
        String namedLocation = "mynamed";
        
        Map<String, String> properties = Maps.newLinkedHashMap();
        
        properties.put("brooklyn.location.named.mynamed", "myprovider");
        
        // prefer those in "named" over everything else
        properties.put("brooklyn.location.named.mynamed.private-key-file", "privateKeyFile-inNamed");
        properties.put("brooklyn.location.myprovider.public-key-file", "publicKeyFile-inProviderSpecific");
        properties.put("brooklyn.location.private-key-data", "privateKeyData-inGeneric");

        Map<String, Object> conf = parser.getLocationProperties(provider, namedLocation, properties);
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
    }
    

    @Test
    public void testThrowsIfProviderDoesNotMatchNamed() throws Exception {
        String provider = "myprovider";
        String namedLocation = "mynamed";
        
        Map<String, String> properties = Maps.newLinkedHashMap();
        
        properties.put("brooklyn.location.named.mynamed", "completelydifferent");

        try {
            Map<String, Object> conf = parser.getLocationProperties(provider, namedLocation, properties);
        } catch (IllegalStateException e) {
            if (!e.toString().contains("Conflicting configuration")) throw e;
        }
    }
}
