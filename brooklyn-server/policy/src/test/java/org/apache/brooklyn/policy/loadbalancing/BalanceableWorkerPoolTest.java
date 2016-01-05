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
package org.apache.brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.group.AbstractGroupImpl;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

public class BalanceableWorkerPoolTest {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceableWorkerPoolTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100;
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    protected BalanceableWorkerPool pool;
    protected Group containerGroup;
    protected Group itemGroup;
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        loc = new SimulatedLocation(MutableMap.of("name", "loc"));
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        containerGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("containerGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockContainerEntity.class)));
        itemGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("itemGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockItemEntity.class)));
        pool = app.createAndManageChild(EntitySpec.create(BalanceableWorkerPool.class));
        pool.setContents(containerGroup, itemGroup);
        
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testDefaultResizeFailsIfContainerGroupNotResizable() throws Exception {
        try {
            pool.resize(1);
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, UnsupportedOperationException.class) == null) throw e;
        }
    }
    
    @Test
    public void testDefaultResizeCallsResizeOnContainerGroup() {
        LocallyResizableGroup resizable = app.createAndManageChild(EntitySpec.create(LocallyResizableGroup.class));
        
        BalanceableWorkerPool pool2 = app.createAndManageChild(EntitySpec.create(BalanceableWorkerPool.class));
        pool2.setContents(resizable, itemGroup);
        
        pool2.resize(123);
        assertEquals(resizable.getCurrentSize(), (Integer) 123);
    }
    
    @Test
    public void testCustomResizableCalledWhenResizing() {
        LocallyResizableGroup resizable = app.createAndManageChild(EntitySpec.create(LocallyResizableGroup.class));
        
        pool.setResizable(resizable);
        
        pool.resize(123);
        assertEquals(resizable.getCurrentSize(), (Integer)123);
    }

    @ImplementedBy(LocallyResizableGroupImpl.class)
    public static interface LocallyResizableGroup extends AbstractGroup, Resizable {
    }
    
    public static class LocallyResizableGroupImpl extends AbstractGroupImpl implements LocallyResizableGroup {
        private int size = 0;

        @Override
        public Integer resize(Integer newSize) {
            size = newSize;
            return size;
        }
        @Override
        public Integer getCurrentSize() {
            return size;
        }
    }
}
