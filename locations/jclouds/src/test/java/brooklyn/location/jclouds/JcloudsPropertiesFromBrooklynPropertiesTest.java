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

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;

public class JcloudsPropertiesFromBrooklynPropertiesTest {
    
    protected static Map<String, Object> sampleProviderOrApiProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.location.jclouds.FooServers.identity", "bob");
        map.put("brooklyn.location.jclouds.FooServers.credential", "s3cr3t");
        map.put("brooklyn.location.jclouds.FooServers.jclouds.ssh.max-retries", "100");
        return map;
    }

    protected static Map<String, Object> sampleNamedProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.location.named.cloudfirst", "jclouds:openstack-nova");
        map.put("brooklyn.location.named.cloudfirst.identity", "myId");
        map.put("brooklyn.location.named.cloudfirst.credential", "password");
        map.put("brooklyn.location.named.cloudfirst.imageId", "RegionOne/1");
        map.put("brooklyn.location.named.cloudfirst.securityGroups", "universal");
        return map;
    }

    protected static Map<String, Object> unsupportedSampleProviderOrApiProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.location.jclouds.FooServers.image-id", "invalid-image-id");
        return map;
    }
    
    protected static Map<String, Object> unsupportedNamedProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.location.named.cloudfirst", "jclouds:openstack-nova");
        map.put("brooklyn.location.named.cloudfirst.hardware-id", "invalid-hardware-id");
        return map;
    }
    
    private JcloudsPropertiesFromBrooklynProperties parser;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        parser = new JcloudsPropertiesFromBrooklynProperties();
    }
    
    @Test
    public void testProviderOrApiProperties() {
        Map<String, Object> map = parser.getJcloudsProperties("FooServers", null, null, sampleProviderOrApiProps());
        Assert.assertEquals(map.get("identity"), "bob");
        Assert.assertEquals(map.get("credential"), "s3cr3t");
        Assert.assertEquals(map.get("provider"), "FooServers");
    }

    @Test
    public void testNamedProperties() {
        Map<String, Object> map = parser.getJcloudsProperties("openstack-nova", null, "cloudfirst", sampleNamedProps());
        Assert.assertEquals(map.get("provider"), "openstack-nova");
        Assert.assertEquals(map.get("identity"), "myId");
        Assert.assertEquals(map.get("credential"), "password");
        Assert.assertEquals(map.get("imageId"), "RegionOne/1");
        Assert.assertEquals(map.get("securityGroups"), "universal");
    }
    
    @Test
    public void testOrderOfPreference() {
        Map<String, Object> allProperties = Maps.newHashMap();
        allProperties.putAll(sampleProviderOrApiProps());
        allProperties.putAll(sampleNamedProps());
        Map<String, Object> map = parser.getJcloudsProperties("openstack-nova", null, "cloudfirst", allProperties);
        Assert.assertEquals(map.get("provider"), "openstack-nova");
        Assert.assertEquals(map.get("identity"), "myId");
        Assert.assertEquals(map.get("credential"), "password");
        Assert.assertEquals(map.get("imageId"), "RegionOne/1");
        Assert.assertEquals(map.get("securityGroups"), "universal");
    }
}
