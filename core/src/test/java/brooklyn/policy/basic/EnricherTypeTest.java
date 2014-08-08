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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.EnricherType;

import com.google.common.collect.ImmutableSet;

public class EnricherTypeTest {
    private MyEnricher enricher;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception{
        enricher = new MyEnricher();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        // nothing to tear down; no management context not started
    }
    
    @Test
    public void testGetConfig() throws Exception {
        EnricherType enricherType = enricher.getEnricherType();
        assertEquals(enricherType.getConfigKeys(), ImmutableSet.of(MyEnricher.CONF1, MyEnricher.CONF2, AbstractEnricher.SUPPRESS_DUPLICATES));
        assertEquals(enricherType.getName(), MyEnricher.class.getCanonicalName());
        assertEquals(enricherType.getConfigKey("test.conf1"), MyEnricher.CONF1);
        assertEquals(enricherType.getConfigKey("test.conf2"), MyEnricher.CONF2);
    }
    
    public static class MyEnricher extends AbstractEnricher {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "test.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "test.conf2", "my descr, conf2", 2);
    }
}
