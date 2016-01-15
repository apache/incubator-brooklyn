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
package org.apache.brooklyn.core.enricher;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationNoEnrichersImpl;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

/**
 * Test that enricher can be created and accessed, by construction and by spec
 */
public class BasicEnricherTest extends BrooklynAppUnitTestSupport {
    
    // TODO These tests are a copy of BasicPolicyTest, which is a code smell.
    // However, the src/main/java code does not contain as much duplication.

    protected void setUpApp() {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class, TestApplicationNoEnrichersImpl.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());
        app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
    }

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
        enricher.config().set(MyEnricher.STR_KEY, "aval");
        enricher.config().set(MyEnricher.INT_KEY, 2);
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getDisplayName(), "Bob");
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
    
    @Test
    public void testAddSpec() throws Exception {
        MyEnricher enricher = app.enrichers().add(EnricherSpec.create(MyEnricher.class)
            .displayName("Bob")
            .configure(MyEnricher.STR_KEY, "aval").configure(MyEnricher.INT_KEY, 2));
        
        assertEquals(enricher.getDisplayName(), "Bob");
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
        
    @Test
    public void testTagsFromSpec() throws Exception {
        MyEnricher enricher = app.enrichers().add(EnricherSpec.create(MyEnricher.class).tag(99).uniqueTag("x"));

        assertEquals(enricher.tags().getTags(), MutableSet.of("x", 99));
        assertEquals(enricher.getUniqueTag(), "x");
    }

    @Test
    public void testSameUniqueTagEnricherNotAddedTwice() throws Exception {
        app.enrichers().add(EnricherSpec.create(MyEnricher.class).tag(99).uniqueTag("x"));
        app.enrichers().add(EnricherSpec.create(MyEnricher.class).tag(94).uniqueTag("x"));
        
        assertEquals(app.getEnrichers().size(), 1);
        // the more recent one should dominate
        Enricher enricher = Iterables.getOnlyElement(app.enrichers());
        Assert.assertTrue(enricher.tags().containsTag(94));
        Assert.assertFalse(enricher.tags().containsTag(99));
    }

}
