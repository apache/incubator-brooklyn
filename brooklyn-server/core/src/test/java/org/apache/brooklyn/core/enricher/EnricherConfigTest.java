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
import static org.testng.Assert.fail;

import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.enricher.BasicEnricherTest.MyEnricher;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

/**
 * Test that configuration properties are usable and inherited correctly.
 */
public class EnricherConfigTest extends BrooklynAppUnitTestSupport {
    
    // TODO These tests are a copy of PolicyConfigTest, which is a code smell.
    // However, the src/main/java code does not contain as much duplication.
    
    private BasicConfigKey<String> differentKey = new BasicConfigKey<String>(String.class, "differentkey", "diffval");

    @Test
    public void testConfigFlagsPassedInAtConstructionIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put("strKey", "aval")
                .put("intKey", 2)
                .build());
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
        // this is set, because key name matches annotation on STR_KEY
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), "aval");
    }
    
    @Test
    public void testUnknownConfigPassedInAtConstructionIsWarnedAndIgnored() throws Exception {
        // TODO Also assert it's warned
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(differentKey, "aval")
                .build());
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(differentKey), null);
        assertEquals(enricher.getEnricherType().getConfigKey(differentKey.getName()), null);
    }
    
    @Test
    public void testConfigPassedInAtConstructionIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "aval")
                .put(MyEnricher.INT_KEY, 2)
                .build());
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
        // this is not set (contrast with above)
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), MyEnricher.STR_KEY_WITH_DEFAULT.getDefaultValue());
    }
    
    @Test
    public void testConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.INT_KEY_WITH_DEFAULT, 0)
                .build());
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY_WITH_DEFAULT), (Integer)0);
    }
    
    @Test
    public void testConfigSetToNullIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY_WITH_DEFAULT, null)
                .build());
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), null);
    }
    
    @Test
    public void testConfigCanBeSetOnEnricher() throws Exception {
        MyEnricher enricher = new MyEnricher();
        enricher.config().set(MyEnricher.STR_KEY, "aval");
        enricher.config().set(MyEnricher.INT_KEY, 2);
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "aval")
                .build());
        enricher.config().set(MyEnricher.STR_KEY, "diffval");
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "diffval");
    }

    @Test
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "origval")
                .build());
        app.enrichers().add(enricher);
        
        try {
            enricher.config().set(MyEnricher.STR_KEY,"newval");
            fail();
        } catch (UnsupportedOperationException e) {
            // success
        }
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "origval");
    }
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        MyEnricher enricher = new MyEnricher();
        app.enrichers().add(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), "str key default");
    }
    
}
