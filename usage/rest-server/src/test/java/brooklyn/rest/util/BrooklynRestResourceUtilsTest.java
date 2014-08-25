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
package brooklyn.rest.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class BrooklynRestResourceUtilsTest {

    private LocalManagementContext managementContext;
    private BrooklynRestResourceUtils util;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        util = new BrooklynRestResourceUtils(managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    @Test
    public void testCreateAppFromImplClass() {
        ApplicationSpec spec = ApplicationSpec.builder()
                .name("myname")
                .type(SampleNoOpApplication.class.getName())
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);
        
        assertEquals(ImmutableList.copyOf(managementContext.getApplications()), ImmutableList.of(app));
        assertEquals(app.getDisplayName(), "myname");
        assertTrue(app instanceof EntityProxy);
        assertTrue(app instanceof MyInterface);
        assertFalse(app instanceof SampleNoOpApplication);
    }

    @Test
    public void testNestedApplications() {
        // hierarchy is: app -> subapp -> subentity (where subentity has a policy)
        
        SampleNoOpApplication app = new SampleNoOpApplication();
        app.setDisplayName("app");
        
        SampleNoOpApplication subapp = new SampleNoOpApplication();
        subapp.setDisplayName("subapp");
        
        TestEntityImpl subentity = new TestEntityImpl(MutableMap.of("displayName", "subentity"), subapp);
        subentity.addPolicy(new MyPolicy(MutableMap.of("name", "mypolicy")));
        subentity.getApplication(); // force this to be cached
        
        app.addChild(subapp);
        Entities.startManagement(app, managementContext);
        
        EntityLocal subappRetrieved = util.getEntity(app.getId(), subapp.getId());
        assertEquals(subappRetrieved.getDisplayName(), "subapp");
        
        EntityLocal subentityRetrieved = util.getEntity(app.getId(), subentity.getId());
        assertEquals(subentityRetrieved.getDisplayName(), "subentity");
        
        Policy subappPolicy = util.getPolicy(app.getId(), subentity.getId(), "mypolicy");
        assertEquals(subappPolicy.getDisplayName(), "mypolicy");
    }

    public interface MyInterface {
    }

    @Catalog(name="Sample No-Op Application",
            description="Application which does nothing, included only as part of the test cases.",
            iconUrl="")
    public static class SampleNoOpApplication extends AbstractApplication implements MyInterface {
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public MyPolicy(Map<String, ?> flags) {
            super(flags);
        }
    }
}
