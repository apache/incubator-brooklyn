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
package org.apache.brooklyn.core.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.testng.annotations.Test;

import com.google.common.base.CaseFormat;

public class ConfigKeysTest {

    @Test
    public void testConvertKeyToLowerHyphen() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("privateKeyFile", "my descr", "my default val");
        ConfigKey<String> key2 = ConfigKeys.convert(key, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
        
        assertEquals(key2.getName(), "private-key-file");
        assertEquals(key2.getType(), String.class);
        assertEquals(key2.getDescription(), "my descr");
        assertEquals(key2.getDefaultValue(), "my default val");
    }
    
    @Test
    public void testConvertKeyToCamelCase() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("private-key-file");
        ConfigKey<String> key2 = ConfigKeys.convert(key, CaseFormat.LOWER_HYPHEN, CaseFormat.LOWER_CAMEL);
        
        assertEquals(key2.getName(), "privateKeyFile");
    }
    
    @Test
    public void testConfigKeyWithPrefix() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("mykey", "my descr", "my default val");
        ConfigKey<String> key2 = ConfigKeys.newConfigKeyWithPrefix("a.b.", key);
        
        assertEquals(key2.getName(), "a.b.mykey");
        assertEquals(key2.getType(), String.class);
        assertEquals(key2.getDescription(), "my descr");
        assertEquals(key2.getDefaultValue(), "my default val");
    }
    
    @Test
    public void testConfigKeyWithoutPrefix() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("a.b.mykey", "my descr", "my default val");
        ConfigKey<String> key2 = ConfigKeys.newConfigKeyWithPrefixRemoved("a.b.", key);
        
        assertEquals(key2.getName(), "mykey");
        assertEquals(key2.getType(), String.class);
        assertEquals(key2.getDescription(), "my descr");
        assertEquals(key2.getDefaultValue(), "my default val");
        
        try {
            ConfigKey<String> key3 = ConfigKeys.newConfigKeyWithPrefixRemoved("wrong.prefix.", key);
            fail("key="+key3);
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    @Test
    public void testConfigKeyBuilder() throws Exception {
        ConfigKey<String> key = ConfigKeys.builder(String.class, "mykey")
            .description("my descr")
            .defaultValue("my default val")
            .inheritance(ConfigInheritance.NONE)
            .reconfigurable(true)
            .build();
        
        checkMyKey(key);
        
        ConfigKey<String> key2 = BasicConfigKey.builder(key).build();
        checkMyKey(key2);
    }

    private void checkMyKey(ConfigKey<String> key) {
        assertEquals(key.getName(), "mykey");
        assertEquals(key.getType(), String.class);
        assertEquals(key.getDescription(), "my descr");
        assertEquals(key.getDefaultValue(), "my default val");
        assertEquals(key.isReconfigurable(), true);
        assertEquals(key.getInheritance(), ConfigInheritance.NONE);
    }

}
