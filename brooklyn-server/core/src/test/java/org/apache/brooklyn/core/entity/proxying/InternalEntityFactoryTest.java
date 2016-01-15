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
package org.apache.brooklyn.core.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.objs.proxy.EntityProxy;
import org.apache.brooklyn.core.objs.proxy.InternalEntityFactory;
import org.apache.brooklyn.core.objs.proxy.InternalPolicyFactory;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationImpl;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InternalEntityFactoryTest {

    private ManagementContextInternal managementContext;
    private InternalEntityFactory factory;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
        InternalPolicyFactory policyFactory = new InternalPolicyFactory(managementContext);
        factory = new InternalEntityFactory(managementContext, managementContext.getEntityManager().getEntityTypeRegistry(), policyFactory);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testCreatesEntity() throws Exception {
        EntitySpec<TestApplication> spec = EntitySpec.create(TestApplication.class);
        TestApplicationImpl app = (TestApplicationImpl) factory.createEntity(spec);
        
        Entity proxy = app.getProxy();
        assertTrue(proxy instanceof Application, "proxy="+app);
        assertFalse(proxy instanceof TestApplicationImpl, "proxy="+app);
        
        assertEquals(proxy.getParent(), null);
        assertSame(proxy.getApplication(), proxy);
    }
    
    @Test
    public void testCreatesProxy() throws Exception {
        TestApplicationImpl app = new TestApplicationImpl();
        EntitySpec<Application> spec = EntitySpec.create(Application.class).impl(TestApplicationImpl.class);
        Application proxy = factory.createEntityProxy(spec, app);
        
        assertFalse(proxy instanceof TestApplicationImpl, "proxy="+app);
        assertTrue(proxy instanceof EntityProxy, "proxy="+app);
    }
    
    @Test
    public void testSetsEntityIsLegacyConstruction() throws Exception {
        TestEntity legacy = new TestEntityImpl();
        assertTrue(legacy.isLegacyConstruction());
        
        TestEntity entity = factory.createEntity(EntitySpec.create(TestEntity.class));
        assertFalse(entity.isLegacyConstruction());
    }
    
    @Test
    public void testCreatesProxyImplementingAdditionalInterfaces() throws Exception {
        MyApplicationImpl app = new MyApplicationImpl();
        EntitySpec<Application> spec = EntitySpec.create(Application.class).impl(MyApplicationImpl.class).additionalInterfaces(MyInterface.class);
        Application proxy = factory.createEntityProxy(spec, app);
        
        assertFalse(proxy instanceof MyApplicationImpl, "proxy="+app);
        assertTrue(proxy instanceof MyInterface, "proxy="+app);
        assertTrue(proxy instanceof EntityProxy, "proxy="+app);
    }
    
    public interface MyInterface {
    }
    
    public static class MyApplicationImpl extends AbstractApplication implements MyInterface {
    }
}
