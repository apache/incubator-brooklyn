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
package brooklyn.entity.proxy;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.TypeCoercions;

@Test
public class ProxySslConfigTest {

    @Test
    public void testFromMap() {
        ProxySslConfig config = TypeCoercions.coerce(MutableMap.of(
            "certificateSourceUrl", "file://tmp/cert.txt", 
            "keySourceUrl", "file://tmp/key.txt", 
            "keyDestination", "dest.txt", 
            "targetIsSsl", true, 
            "reuseSessions", true), 
            ProxySslConfig.class);
        Assert.assertEquals(config.getCertificateSourceUrl(), "file://tmp/cert.txt");
        Assert.assertEquals(config.getKeySourceUrl(), "file://tmp/key.txt");
        Assert.assertEquals(config.getKeyDestination(), "dest.txt");
        Assert.assertEquals(config.getTargetIsSsl(), true);
        Assert.assertEquals(config.getReuseSessions(), true);
    }
    
    @Test
    public void testFromMapWithNullsAndDefaults() {
        ProxySslConfig config = TypeCoercions.coerce(MutableMap.of(
            "certificateSourceUrl", "file://tmp/cert.txt", 
            "keySourceUrl", null, 
            "targetIsSsl", "false"), 
            ProxySslConfig.class);
        Assert.assertEquals(config.getCertificateSourceUrl(), "file://tmp/cert.txt");
        Assert.assertEquals(config.getKeySourceUrl(), null);
        Assert.assertEquals(config.getKeyDestination(), null);
        Assert.assertEquals(config.getTargetIsSsl(), false);
        Assert.assertEquals(config.getReuseSessions(), false);
    }
    
}
