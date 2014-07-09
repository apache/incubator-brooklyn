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
package io.brooklyn.camp.brooklyn;

import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.event.basic.Sensors;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.task.Tasks;

import com.google.common.collect.Iterables;
import com.google.common.io.Files;

@Test
public class DslAndRebindYamlTest extends AbstractYamlTest {
    
    private static final Logger log = LoggerFactory.getLogger(DslAndRebindYamlTest.class);
    
    protected ClassLoader classLoader = getClass().getClassLoader();
    protected File mementoDir;
    protected Set<ManagementContext> mgmtContexts = MutableSet.of();

    @Override
    protected LocalManagementContext newTestManagementContext() {
        if (mementoDir!=null) throw new IllegalStateException("already created mgmt context");
        mementoDir = Files.createTempDir();
        LocalManagementContext mgmt = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        mgmtContexts.add(mgmt);
        return mgmt;
    }
    
    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() {
        for (ManagementContext mgmt: mgmtContexts) Entities.destroyAll(mgmt);
        super.tearDown();
        mementoDir = null;
        mgmtContexts.clear();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    public Application rebind(Application app) throws Exception {
        RebindTestUtils.waitForPersisted(app);
        // not strictly needed, but for good measure:
        RebindTestUtils.checkCurrentMementoSerializable(app);
        Application result = RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        mgmtContexts.add(result.getManagementContext());
        return result;
    }


    protected Entity setupAndCheckTestEntityInBasicYamlWith(String ...extras) throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml", extras));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");

        log.info("App started:");
        Entities.dumpInfo(app);
        
        Assert.assertTrue(app.getChildren().iterator().hasNext(), "Expected app to have child entity");
        Entity entity = app.getChildren().iterator().next();
        Assert.assertTrue(entity instanceof TestEntity, "Expected TestEntity, found " + entity.getClass());
        
        return (TestEntity)entity;
    }

    public static <T> T getConfigInTask(final Entity entity, final ConfigKey<T> key) {
        return Entities.submit(entity, Tasks.<T>builder().body(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return entity.getConfig(key);
            }
        }).build()).getUnchecked();
    }
    
    @Test
    public void testDslAttributeWhenReady() throws Exception {
        Entity testEntity = entityWithAttributeWhenReady();
        ((EntityInternal)testEntity).setAttribute(Sensors.newStringSensor("foo"), "bar");
        Assert.assertEquals(getConfigInTask(testEntity, TestEntity.CONF_NAME), "bar");
    }

    @Test
    public void testDslAttributeWhenReadyRebind() throws Exception {
        Entity testEntity = entityWithAttributeWhenReady();
        ((EntityInternal)testEntity).setAttribute(Sensors.newStringSensor("foo"), "bar");
        Application app2 = rebind(testEntity.getApplication());
        Entity e2 = Iterables.getOnlyElement( app2.getChildren() );
        
        Assert.assertEquals(getConfigInTask(e2, TestEntity.CONF_NAME), "bar");
    }

    private Entity entityWithAttributeWhenReady() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.confName: $brooklyn:component(\"x\").attributeWhenReady(\"foo\")");
    }


    @Test
    public void testDslConfigFromRoot() throws Exception {
        Entity testEntity = entityWithConfigFromRoot();
        Assert.assertEquals(getConfigInTask(testEntity, TestEntity.CONF_NAME), "bar");
    }

    @Test
    public void testDslConfigFromRootRebind() throws Exception {
        Entity testEntity = entityWithConfigFromRoot();
        Application app2 = rebind(testEntity.getApplication());
        Entity e2 = Iterables.getOnlyElement( app2.getChildren() );
        
        Assert.assertEquals(getConfigInTask(e2, TestEntity.CONF_NAME), "bar");
    }

    private Entity entityWithConfigFromRoot() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.confName: $brooklyn:component(\"x\").config(\"foo\")",
            "brooklyn.config:",
            "  foo: bar");
    }


    @Test
    public void testDslFormatString() throws Exception {
        Entity testEntity = entityWithFormatString();
        Assert.assertEquals(getConfigInTask(testEntity, TestEntity.CONF_NAME), "hello world");
    }

    @Test
    public void testDslFormatStringRebind() throws Exception {
        Entity testEntity = entityWithFormatString();
        Application app2 = rebind(testEntity.getApplication());
        Entity e2 = Iterables.getOnlyElement( app2.getChildren() );
        
        Assert.assertEquals(getConfigInTask(e2, TestEntity.CONF_NAME), "hello world");
    }

    private Entity entityWithFormatString() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.confName: $brooklyn:formatString(\"hello %s\", \"world\")");
    }

}
