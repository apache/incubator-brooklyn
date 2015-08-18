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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

@Test
public class ApplicationsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ApplicationsYamlTest.class);

    @Test
    public void testWrapsEntity() throws Exception {
        Entity app = createAndStartApplication(
                "services:",
                "- type: " + BasicEntity.class.getName());
        assertTrue(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertTrue(Iterables.getOnlyElement(app.getChildren()) instanceof BasicEntity);
    }

    @Test
    public void testDoesNotWrapApp() throws Exception {
        Entity app = createAndStartApplication(
                "services:",
                "- type: " + BasicApplication.class.getName());
        assertNull(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertTrue(app.getChildren().isEmpty());
    }

    @Test
    public void testWrapsAppIfForced() throws Exception {
        Entity app = createAndStartApplication(
                "wrappedApp: true",
                "services:",
                "- type: " + BasicApplication.class.getName());
        assertTrue(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertTrue(Iterables.getOnlyElement(app.getChildren()) instanceof BasicApplication);
        assertTrue(Iterables.getOnlyElement(app.getChildren()).getChildren().isEmpty());
    }

    @Test
    public void testDoesNotWrapAppIfUnforced() throws Exception {
        Entity app = createAndStartApplication(
                "wrappedApp: false",
                "services:",
                "- type: " + BasicApplication.class.getName());
        assertNull(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertTrue(app.getChildren().isEmpty());
    }
    
    @Test
    public void testWrapsEntityIfDifferentTopLevelName() throws Exception {
        Entity app = createAndStartApplication(
                "name: topLevel",
                "services:",
                "- type: " + BasicApplication.class.getName(),
                "  name: bottomLevel");
        assertTrue(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertEquals(app.getDisplayName(), "topLevel");
        assertTrue(Iterables.getOnlyElement(app.getChildren()) instanceof BasicApplication);
        assertTrue(Iterables.getOnlyElement(app.getChildren()).getChildren().isEmpty());
        assertEquals(Iterables.getOnlyElement(app.getChildren()).getDisplayName(), "bottomLevel");
    }
    
    @Test
    public void testDoesNotWrapsEntityIfNoNameOnService() throws Exception {
        Entity app = createAndStartApplication(
                "name: topLevel",
                "services:",
                "- type: " + BasicApplication.class.getName());
        assertNull(app.getConfig(EntityManagementUtils.WRAPPER_APP_MARKER));
        assertTrue(app instanceof BasicApplication);
        assertTrue(app.getChildren().isEmpty());
        assertEquals(app.getDisplayName(), "topLevel");
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }

}
