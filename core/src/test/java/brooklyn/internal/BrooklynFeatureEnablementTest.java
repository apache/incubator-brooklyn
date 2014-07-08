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
package brooklyn.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BrooklynFeatureEnablementTest {

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        BrooklynFeatureEnablement.clearCache();
    }
    
    @Test
    public void testDefaultIsNotEnabled() throws Exception {
        assertFalse(BrooklynFeatureEnablement.isEnabled("feature.not.referenced.anywhere"));
    }
    
    @Test
    public void testCanSetPropertyEnablement() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetPropertyEnablement";
        boolean preTestVal = BrooklynFeatureEnablement.isEnabled(featureProperty);
        try {
            boolean oldVal = BrooklynFeatureEnablement.enable(featureProperty);
            assertEquals(oldVal, preTestVal);
            assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
            
            boolean oldVal2 = BrooklynFeatureEnablement.disable(featureProperty);
            assertTrue(oldVal2);
            assertFalse(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            BrooklynFeatureEnablement.setEnablement(featureProperty, preTestVal);
        }
    }
    
    @Test
    public void testReadsEnablementFromProperties() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testReadsEnablementFromProperties";
        System.setProperty(featureProperty, "true");
        try {
            assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            System.clearProperty(featureProperty);
        }
    }
    
    @Test
    public void testCanSetDefaultWhichTakesEffectIfNoSystemProperty() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetDefaultWhichTakesEffectIfNoSystemProperty";
        BrooklynFeatureEnablement.setDefault(featureProperty, true);
        assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
        System.setProperty(featureProperty, "true");
        try {
        } finally {
            System.clearProperty(featureProperty);
        }
    }
    
    @Test
    public void testCanSetDefaultWhichIsIgnoredIfSystemProperty() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetDefaultWhichIsIgnoredIfSystemProperty";
        System.setProperty(featureProperty, "false");
        try {
            BrooklynFeatureEnablement.setDefault(featureProperty, true);
            assertFalse(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            System.clearProperty(featureProperty);
        }
    }
}
