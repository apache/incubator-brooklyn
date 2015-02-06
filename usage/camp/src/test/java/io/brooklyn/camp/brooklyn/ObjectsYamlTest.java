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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.trait.Configurable;
import brooklyn.management.ManagementContext;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;

@Test
public class ObjectsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ObjectsYamlTest.class);

    private static final AtomicBoolean managementContextInjected = new AtomicBoolean(false);
    private static final List<String> configKeys = Lists.newLinkedList();

    public static class TestObject implements ManagementContextInjectable {
        private String string;
        private Integer number;
        private Object object;

        public TestObject() { }

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }

        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }

        public Object getObject() { return object; }
        public void setObject(Object object) { this.object = object; }

        @Override
        public void injectManagementContext(ManagementContext managementContext) {
            log.info("Detected injection of {}", managementContext);
            managementContextInjected.set(true);
        }
    }

    public static class ConfigurableObject implements Configurable {
        public static final ConfigKey<Integer> INTEGER = ConfigKeys.newIntegerConfigKey("config.number");
        @SetFromFlag("object")
        public static final ConfigKey<Object> OBJECT = ConfigKeys.newConfigKey(Object.class, "config.object");

        @SetFromFlag("flag")
        private String string;

        private Integer number;
        private Object object;
        private Double value;

        public ConfigurableObject() { }

        public String getString() { return string; }

        public Integer getNumber() { return number; }

        public Object getObject() { return object; }

        public Double getDouble() { return value; }
        public void setDouble(Double value) { this.value = value; }

        @Override
        public <T> T setConfig(ConfigKey<T> key, T value) {
            log.info("Detected configuration injection for {}: {}", key.getName(), value);
            configKeys.add(key.getName());
            if ("config.number".equals(key.getName())) number = TypeCoercions.coerce(value, Integer.class);
            if ("config.object".equals(key.getName())) object = value;
            return value;
        }
    }

    protected Entity setupAndCheckTestEntityInBasicYamlWith(String ...extras) throws Exception {
        managementContextInjected.set(false);
        configKeys.clear();
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

    @Test
    public void testSingleEntity() throws Exception {
        setupAndCheckTestEntityInBasicYamlWith();
    }

    @Test
    public void testBrooklynObject() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confObject:",
            "      $brooklyn:object:",
            "        type: io.brooklyn.camp.brooklyn.ObjectsYamlTest$TestObject",
            "        object.fields:",
            "          number: 7",
            "          object:",
            "            $brooklyn:object:",
            "              type: brooklyn.entity.proxy.ProxySslConfig",
            "          string: \"frog\"");

        Object testObject = testEntity.getConfig(TestEntity.CONF_OBJECT);

        Assert.assertTrue(testObject instanceof TestObject, "Expected a TestObject: "+testObject);
        Assert.assertTrue(managementContextInjected.get());
        Assert.assertEquals(((TestObject) testObject).getNumber(), Integer.valueOf(7));
        Assert.assertEquals(((TestObject) testObject).getString(), "frog");

        Object testObjectObject = ((TestObject) testObject).getObject();
        Assert.assertTrue(testObjectObject instanceof ProxySslConfig, "Expected a ProxySslConfig: "+testObjectObject);
    }

    @Test
    public void testBrooklynConfigurableObject() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confObject:",
            "      $brooklyn:object:",
            "        type: io.brooklyn.camp.brooklyn.ObjectsYamlTest$ConfigurableObject",
            "        object.fields:",
            "          double: 1.4",
            "        brooklyn.config:",
            "          flag: frog",
            "          config.number: 7",
            "          object:",
            "            $brooklyn:object:",
            "              type: brooklyn.entity.proxy.ProxySslConfig");

        Object testObject = testEntity.getConfig(TestEntity.CONF_OBJECT);

        Assert.assertTrue(testObject instanceof ConfigurableObject, "Expected a ConfigurableObject: "+testObject);
        Assert.assertEquals(((ConfigurableObject) testObject).getDouble(), Double.valueOf(1.4));
        Assert.assertEquals(((ConfigurableObject) testObject).getString(), "frog");
        Assert.assertEquals(((ConfigurableObject) testObject).getNumber(), Integer.valueOf(7));

        Object testObjectObject = ((ConfigurableObject) testObject).getObject();
        Assert.assertTrue(testObjectObject instanceof ProxySslConfig, "Expected a ProxySslConfig: "+testObjectObject);

        Assert.assertTrue(configKeys.contains(ConfigurableObject.INTEGER.getName()), "Expected INTEGER key: "+configKeys);
        Assert.assertTrue(configKeys.contains(ConfigurableObject.OBJECT.getName()), "Expected OBJECT key: "+configKeys);
    }

    @Test
    public void testBrooklynObjectPrefix() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confListPlain:",
            "    - $brooklyn:object:",
            "        objectType: brooklyn.entity.proxy.ProxySslConfig",
            "    - $brooklyn:object:",
            "        object_type: brooklyn.entity.proxy.ProxySslConfig",
            "    - $brooklyn:object:",
            "        type: brooklyn.entity.proxy.ProxySslConfig");

        List<?> testList = testEntity.getConfig(TestEntity.CONF_LIST_PLAIN);

        Assert.assertEquals(testList.size(), 3);
        for (Object entry : testList) {
            Assert.assertTrue(entry instanceof ProxySslConfig, "Expected a ProxySslConfig: "+entry);
        }
    }

    @Test
    public void testBrooklynObjectWithFunction() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confObject:",
            "      $brooklyn:object:",
            "        type: io.brooklyn.camp.brooklyn.ObjectsYamlTest$TestObject",
            "        object.fields:",
            "          number: 7",
            "          object:",
            "            $brooklyn:object:",
            "              type: brooklyn.entity.proxy.ProxySslConfig",
            "          string:",
            "            $brooklyn:formatString(\"%s\", \"frog\")");

        Object testObject = testEntity.getConfig(TestEntity.CONF_OBJECT);

        Assert.assertTrue(testObject instanceof TestObject, "Expected a TestObject: "+testObject);
        Assert.assertTrue(managementContextInjected.get());
        Assert.assertEquals(((TestObject) testObject).getNumber(), Integer.valueOf(7));
        Assert.assertEquals(((TestObject) testObject).getString(), "frog");

        Object testObjectObject = ((TestObject) testObject).getObject();
        Assert.assertTrue(testObjectObject instanceof ProxySslConfig, "Expected a ProxySslConfig: "+testObjectObject);
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

}
