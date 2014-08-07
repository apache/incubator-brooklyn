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
package brooklyn.policy.basic;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.flags.SetFromFlag;

/**
 * Test that policy can be created and accessed, by construction and by spec
 */
public class BasicPolicyTest extends BrooklynAppUnitTestSupport {
    
    public static class MyPolicy extends AbstractPolicy {
        @SetFromFlag("intKey")
        public static final BasicConfigKey<Integer> INT_KEY = new BasicConfigKey<Integer>(Integer.class, "bkey", "b key");
        
        @SetFromFlag("strKey")
        public static final ConfigKey<String> STR_KEY = new BasicConfigKey<String>(String.class, "akey", "a key");
        public static final ConfigKey<Integer> INT_KEY_WITH_DEFAULT = new BasicConfigKey<Integer>(Integer.class, "ckey", "c key", 1);
        public static final ConfigKey<String> STR_KEY_WITH_DEFAULT = new BasicConfigKey<String>(String.class, "strKey", "str key", "str key default");
        
        MyPolicy(Map<?,?> flags) {
            super(flags);
        }
        
        public MyPolicy() {
            super();
        }
    }
    
    @Test
    public void testAddInstance() throws Exception {
        MyPolicy policy = new MyPolicy();
        policy.setDisplayName("Bob");
        policy.setConfig(MyPolicy.STR_KEY, "aval");
        policy.setConfig(MyPolicy.INT_KEY, 2);
        app.addPolicy(policy);
        
        assertEquals(policy.getDisplayName(), "Bob");
        assertEquals(policy.getConfig(MyPolicy.STR_KEY), "aval");
        assertEquals(policy.getConfig(MyPolicy.INT_KEY), (Integer)2);
    }
    
    @Test
    public void testAddSpec() throws Exception {
        MyPolicy policy = app.addPolicy(PolicySpec.create(MyPolicy.class)
            .displayName("Bob")
            .configure(MyPolicy.STR_KEY, "aval").configure(MyPolicy.INT_KEY, 2));
        
        assertEquals(policy.getDisplayName(), "Bob");
        assertEquals(policy.getConfig(MyPolicy.STR_KEY), "aval");
        assertEquals(policy.getConfig(MyPolicy.INT_KEY), (Integer)2);
    }
        
    @Test
    public void testTagsFromSpec() throws Exception {
        MyPolicy policy = app.addPolicy(PolicySpec.create(MyPolicy.class).tag(99).uniqueTag("x"));

        assertEquals(policy.getTagSupport().getTags(), MutableSet.of("x", 99));
        assertEquals(policy.getUniqueTag(), "x");
    }

}
