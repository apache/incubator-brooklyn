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
package brooklyn.config;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.basic.ConfigKeys;

public class ConfigUtilsTest {

    public static final ConfigKey<String> S1 = ConfigKeys.newStringConfigKey("s1");
    public final ConfigKey<String> S2 = ConfigKeys.newStringConfigKey("s2");
    
    @Test
    public void testGetStaticKeys() {
        Set<HasConfigKey<?>> keys = ConfigUtils.getStaticKeysOnClass(ConfigUtilsTest.class);
        if (keys.size()!=1) Assert.fail("Expected 1 key; got: "+keys);
        Assert.assertEquals(keys.iterator().next().getConfigKey(), S1);
    }
}
