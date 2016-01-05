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
package org.apache.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.StringReader;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

@Test
public class AppYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(AppYamlTest.class);

    @Test
    public void testAutoWrapsEntityInApp() throws Exception {
        String yaml = Joiner.on("\n").join(
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity");
        
        BasicApplication app = (BasicApplication) createStartWaitAndLogApplication(new StringReader(yaml));
        @SuppressWarnings("unused")
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
    }
    
    @Test
    public void testDoesNotAutoWrapApp() throws Exception {
        String yaml = Joiner.on("\n").join(
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication");
        
        TestApplication app = (TestApplication) createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getChildren().isEmpty());
    }
    
    @Test
    public void testWrapsAppIfNameAtTopLevelAndOnApp() throws Exception {
        String yaml = Joiner.on("\n").join(
                "name: myTopLevelName",
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication",
                "  name: myEntityName");
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        assertNull(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertEquals(app.getDisplayName(), "myTopLevelName");
        assertEquals(app.getChildren().size(), 0);
    }
    
    @Test
    public void testDoesNotWrapAppIfNoConflictingNameOnApp() throws Exception {
        String yaml = Joiner.on("\n").join(
                "name: myTopLevelName",
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication");
        
        TestApplication app = (TestApplication) createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getChildren().isEmpty());
        assertEquals(app.getDisplayName(), "myTopLevelName");
    }
    
    @Test
    public void testDoesNotWrapAppWithDefaultDisplayName() throws Exception {
        String yaml = Joiner.on("\n").join(
                "name: myTopLevelName",
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication",
                "  brooklyn.config:",
                "    defaultDisplayName: myDefaultEntityName");
        
        TestApplication app = (TestApplication) createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getChildren().isEmpty());
        assertEquals(app.getDisplayName(), "myTopLevelName");
    }
    
    @Test
    public void testUsesDefaultDisplayNameIfNoOther() throws Exception {
        String yaml = Joiner.on("\n").join(
                "services:",
                "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication",
                "  brooklyn.config:",
                "    defaultDisplayName: myDefaultEntityName");
        
        TestApplication app = (TestApplication) createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getChildren().isEmpty());
        assertEquals(app.getDisplayName(), "myDefaultEntityName");
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
}
