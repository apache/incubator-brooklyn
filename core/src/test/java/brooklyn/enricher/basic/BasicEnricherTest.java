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
package brooklyn.enricher.basic;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.EnricherSpec;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.flags.SetFromFlag;

/**
 * Test that enricher can be created and accessed, by construction and by spec
 */
public class BasicEnricherTest extends BrooklynAppUnitTestSupport {
    
    // TODO These tests are a copy of BasicPolicyTest, which is a code smell.
    // However, the src/main/java code does not contain as much duplication.
    
    public static class MyEnricher extends AbstractEnricher {
        @SetFromFlag("intKey")
        public static final BasicConfigKey<Integer> INT_KEY = new BasicConfigKey<Integer>(Integer.class, "bkey", "b key");
        
        @SetFromFlag("strKey")
        public static final ConfigKey<String> STR_KEY = new BasicConfigKey<String>(String.class, "akey", "a key");
        public static final ConfigKey<Integer> INT_KEY_WITH_DEFAULT = new BasicConfigKey<Integer>(Integer.class, "ckey", "c key", 1);
        public static final ConfigKey<String> STR_KEY_WITH_DEFAULT = new BasicConfigKey<String>(String.class, "strKey", "str key", "str key default");
        
        MyEnricher(Map<?,?> flags) {
            super(flags);
        }
        
        public MyEnricher() {
            super();
        }
    }
    
    @Test
    public void testAddInstance() throws Exception {
        MyEnricher enricher = new MyEnricher();
        enricher.setDisplayName("Bob");
        enricher.setConfig(MyEnricher.STR_KEY, "aval");
        enricher.setConfig(MyEnricher.INT_KEY, 2);
        app.addEnricher(enricher);
        
        assertEquals(enricher.getDisplayName(), "Bob");
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
    
    @Test
    public void testAddSpec() throws Exception {
        MyEnricher enricher = app.addEnricher(EnricherSpec.create(MyEnricher.class)
            .displayName("Bob")
            .configure(MyEnricher.STR_KEY, "aval").configure(MyEnricher.INT_KEY, 2));
        
        assertEquals(enricher.getDisplayName(), "Bob");
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
        
    @Test
    public void testTagsFromSpec() throws Exception {
        MyEnricher enricher = app.addEnricher(EnricherSpec.create(MyEnricher.class).tag(99).uniqueTag("x"));

        assertEquals(enricher.getTagSupport().getTags(), MutableSet.of("x", 99));
        assertEquals(enricher.getUniqueTag(), "x");
    }

    @Test
    public void testSameUniqueTagEnricherNotAddedTwice() throws Exception {
        MyEnricher enricher1 = app.addEnricher(EnricherSpec.create(MyEnricher.class).tag(99).uniqueTag("x"));
        MyEnricher enricher2 = app.addEnricher(EnricherSpec.create(MyEnricher.class).tag(94).uniqueTag("x"));
        assertEquals(enricher2, enricher1);
        assertEquals(app.getEnrichers().size(), 1);
    }

}
