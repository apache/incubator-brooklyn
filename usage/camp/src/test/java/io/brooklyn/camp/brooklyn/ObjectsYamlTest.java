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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.test.entity.TestEntity;

@Test
public class ObjectsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ObjectsYamlTest.class);

    public static class TestObject implements ManagementContextInjectable {
        private String string;
        private Integer number;

        public TestObject() { }

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }

        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }

        @Override
        public void injectManagementContext(ManagementContext managementContext) {
            log.info("Detected injection of {}", managementContext);
        }
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

    @Test
    public void testSingleEntity() throws Exception {
        setupAndCheckTestEntityInBasicYamlWith();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBrooklynObject() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confObject:",
            "      $brooklyn:object:",
            "        objectType: io.brooklyn.camp.brooklyn.ObjectsYamlTest$TestObject",
            "        object.fields:",
            "          number: 5",
            "          string: \"frog\"");

        Object testObject = testEntity.getConfig(TestEntity.CONF_OBJECT);

        Assert.assertTrue(testObject instanceof TestObject);
        Assert.assertEquals(((TestObject) testObject).getNumber(), Integer.valueOf(5));
        Assert.assertEquals(((TestObject) testObject).getString(), "frog");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBrooklynObjectWithFunction() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confObject:",
            "      $brooklyn:object:",
            "        objectType: io.brooklyn.camp.brooklyn.ObjectsYamlTest$TestObject",
            "        object.fields:",
            "          number: 7",
            "          string:",
            "            $brooklyn:formatString(\"%s\", \"7\")");

        Object testObject = testEntity.getConfig(TestEntity.CONF_OBJECT);

        Assert.assertTrue(testObject instanceof TestObject);
        Assert.assertEquals(((TestObject) testObject).getNumber(), Integer.valueOf(7));
        Assert.assertEquals(((TestObject) testObject).getString(), "7");
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

}
