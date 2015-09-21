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

import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.task.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

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
        ((EntityInternal)testEntity).sensors().set(Sensors.newStringSensor("foo"), "bar");
        Assert.assertEquals(getConfigInTask(testEntity, TestEntity.CONF_NAME), "bar");
    }

    @Test
    public void testDslAttributeWhenReadyRebind() throws Exception {
        Entity testEntity = entityWithAttributeWhenReady();
        ((EntityInternal)testEntity).sensors().set(Sensors.newStringSensor("foo"), "bar");
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

    private void doTestOnEntityWithSensor(Entity testEntity, Sensor<?> expectedSensor) throws Exception {
        doTestOnEntityWithSensor(testEntity, expectedSensor, true);
    }
    private void doTestOnEntityWithSensor(Entity testEntity, Sensor<?> expectedSensor, boolean inTask) throws Exception {
        @SuppressWarnings("rawtypes")
        ConfigKey<Sensor> configKey = ConfigKeys.newConfigKey(Sensor.class, "test.sensor");
        Sensor<?> s;
        s = inTask ? getConfigInTask(testEntity, configKey) : testEntity.getConfig(configKey);
        Assert.assertEquals(s, expectedSensor);
        Application app2 = rebind(testEntity.getApplication());
        Entity te2 = Iterables.getOnlyElement( app2.getChildren() );
        s = inTask ? getConfigInTask(te2, configKey) : te2.getConfig(configKey);
        Assert.assertEquals(s, expectedSensor);
    }
    
    @Test
    public void testDslSensorFromClass() throws Exception {
        doTestOnEntityWithSensor(entityWithSensorFromClass(), Attributes.SERVICE_UP);
        // without context it can still find it
        doTestOnEntityWithSensor(entityWithSensorFromClass(), Attributes.SERVICE_UP, false);
    }
    @Test
    public void testDslSensorLocal() throws Exception {
        doTestOnEntityWithSensor(entityWithSensorLocal(), TestEntity.SEQUENCE);
        // here without context it makes one up, so type info (and description etc) not present; 
        // but context is needed to submit the DslDeferredSupplier object, so this would fail
//        doTestOnEntityWithSensor(entityWithSensorAdHoc(), Sensors.newSensor(Object.class, TestEntity.SEQUENCE.getName()), false);
    }
    @Test
    public void testDslSensorAdHoc() throws Exception {
        doTestOnEntityWithSensor(entityWithSensorAdHoc(), Sensors.newSensor(Object.class, "sensor.foo"));
        // here context has no impact, but it is needed to submit the DslDeferredSupplier object so this would fail
//        doTestOnEntityWithSensor(entityWithSensorAdHoc(), Sensors.newSensor(Object.class, "sensor.foo"), false);
    }
    
    private Entity entityWithSensorFromClass() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.sensor: $brooklyn:sensor(\""+Attributes.class.getName()+"\", \""+Attributes.SERVICE_UP.getName()+"\")");
    }

    private Entity entityWithSensorLocal() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.sensor: $brooklyn:sensor(\""+TestEntity.SEQUENCE.getName()+"\")");
    }

    private Entity entityWithSensorAdHoc() throws Exception {
        return setupAndCheckTestEntityInBasicYamlWith( 
            "  id: x",
            "  brooklyn.config:",
            "    test.sensor: $brooklyn:sensor(\"sensor.foo\")");
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


    /*
           - type: org.apache.brooklyn.enricher.stock.Transformer
          brooklyn.config:
            enricher.sourceSensor: $brooklyn:sensor("mongodb.server.replicaSet.primary.endpoint")
            enricher.targetSensor: $brooklyn:sensor("justtheport")
            enricher.transformation: $brooklyn:function.regexReplacement("^.*:", "")
        - type: org.apache.brooklyn.enricher.stock.Transformer
          brooklyn.config:
            enricher.sourceSensor: $brooklyn:sensor("mongodb.server.replicaSet.primary.endpoint")
            enricher.targetSensor: $brooklyn:sensor("directport")
            enricher.targetValue: $brooklyn:regexReplacement($brooklyn:attributeWhenReady("mongodb.server.replicaSet.primary.endpoint"), "^.*:", "foo")
     */

    @Test
    public void testRegexReplacementWithStrings() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
                "  brooklyn.config:",
                "    test.regex.config: $brooklyn:regexReplacement(\"somefooname\", \"foo\", \"bar\")"
        );
        Assert.assertEquals("somebarname", testEntity.getConfig(ConfigKeys.newStringConfigKey("test.regex.config")));
    }

    @Test
    public void testRegexReplacementWithAttributeWhenReady() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
                "  brooklyn.config:",
                "    test.regex.config: $brooklyn:regexReplacement($brooklyn:attributeWhenReady(\"test.regex.source\"), $brooklyn:attributeWhenReady(\"test.regex.pattern\"), $brooklyn:attributeWhenReady(\"test.regex.replacement\"))"
        );
        testEntity.sensors().set(Sensors.newStringSensor("test.regex.source"), "somefooname");
        testEntity.sensors().set(Sensors.newStringSensor("test.regex.pattern"), "foo");
        testEntity.sensors().set(Sensors.newStringSensor("test.regex.replacement"), "bar");

        Assert.assertEquals("somebarname", testEntity.getConfig(ConfigKeys.newStringConfigKey("test.regex.config")));
    }

    @Test
    public void testRegexReplacementFunctionWithStrings() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
                "  brooklyn.enrichers:",
                "  - type: org.apache.brooklyn.enricher.stock.Transformer",
                "    brooklyn.config:",
                "      enricher.sourceSensor: $brooklyn:sensor(\"test.name\")",
                "      enricher.targetSensor: $brooklyn:sensor(\"test.name.transformed\")",
                "      enricher.transformation: $brooklyn:function.regexReplacement(\"foo\", \"bar\")"
        );
        testEntity.sensors().set(TestEntity.NAME, "somefooname");
        AttributeSensor<String> transformedSensor = Sensors.newStringSensor("test.name.transformed");
        EntityTestUtils.assertAttributeEqualsEventually(testEntity, transformedSensor, "somebarname");
    }

    @Test
    public void testRegexReplacementFunctionWithAttributeWhenReady() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
                "  brooklyn.enrichers:",
                "  - type: org.apache.brooklyn.enricher.stock.Transformer",
                "    brooklyn.config:",
                "      enricher.sourceSensor: $brooklyn:sensor(\"test.name\")",
                "      enricher.targetSensor: $brooklyn:sensor(\"test.name.transformed\")",
                "      enricher.transformation: $brooklyn:function.regexReplacement($brooklyn:attributeWhenReady(\"test.pattern\"), $brooklyn:attributeWhenReady(\"test.replacement\"))"
        );
        testEntity.sensors().set(Sensors.newStringSensor("test.pattern"), "foo");
        testEntity.sensors().set(Sensors.newStringSensor("test.replacement"), "bar");
        testEntity.sensors().set(TestEntity.NAME, "somefooname");
        AttributeSensor<String> transformedSensor = Sensors.newStringSensor("test.name.transformed");
        EntityTestUtils.assertAttributeEqualsEventually(testEntity, transformedSensor, "somebarname");
    }

}
