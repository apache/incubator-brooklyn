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

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.task.Tasks;

import com.google.common.collect.Iterables;

@Test
public class MapReferenceYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(MapReferenceYamlTest.class);

    protected Entity setupAndCheckTestEntityInBasicYamlWith(String ...extras) throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-reference-map-template.yaml", extras));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getDisplayName(), "test-entity-reference-map-template");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(Iterables.size(app.getChildren()), 3, "Expected app to have child entity");
        Iterable<BasicEntity> basicEntities = Iterables.filter(app.getChildren(), BasicEntity.class);
        Iterable<TestEntity> testEntities = Iterables.filter(app.getChildren(), TestEntity.class);
        Assert.assertEquals(Iterables.size(basicEntities), 2, "Expected app to have two basic entities");
        Assert.assertEquals(Iterables.size(testEntities), 1, "Expected app to have one test entity");

        return Iterables.getOnlyElement(testEntities);
    }

    @Test
    public void testSingleEntity() throws Exception {
        setupAndCheckTestEntityInBasicYamlWith();
    }

    @Test
    public void testBrooklynConfigWithMapFunction() throws Exception {
        final Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confMapThing.obj:",
            "      frog: $brooklyn:formatString(\"%s\", \"frog\")",
            "      object:",
            "        $brooklyn:object:",
            "          type: brooklyn.entity.proxy.ProxySslConfig",
            "      one: $brooklyn:entity(\"one\")",
            "      two: $brooklyn:entity(\"two\")");

        Map<?,?> testMap = (Map<?,?>) Entities.submit(testEntity, Tasks.builder().body(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return testEntity.getConfig(TestEntity.CONF_MAP_THING_OBJECT);
            }
        }).build()).get();
        Object frog = testMap.get("frog");
        Object one = testMap.get("one");
        Object two = testMap.get("two");
        Object object = testMap.get("object");

        Assert.assertTrue(frog instanceof String, "Should have found a String: " + frog);
        Assert.assertEquals(frog, "frog", "Should have found a formatted String: " + frog);
        Assert.assertTrue(object instanceof ProxySslConfig, "Should have found a ProxySslConfig: " + object);
        Assert.assertTrue(one instanceof BasicEntity, "Should have found a BasicEntity: " + one);
        Assert.assertTrue(two instanceof BasicEntity, "Should have found a BasicEntity: " + two);
    }

    @Test
    public void testBrooklynConfigWithPlainMapFunction() throws Exception {
        final Entity testEntity = setupAndCheckTestEntityInBasicYamlWith(
            "  brooklyn.config:",
            "    test.confMapPlain:",
            "      frog: $brooklyn:formatString(\"%s\", \"frog\")",
            "      object:",
            "        $brooklyn:object:",
            "          type: brooklyn.entity.proxy.ProxySslConfig",
            "      one: $brooklyn:entity(\"one\")",
            "      two: $brooklyn:entity(\"two\")");

        Map<?,?> testMap = (Map<?,?>) Entities.submit(testEntity, Tasks.builder().body(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return testEntity.getConfig(TestEntity.CONF_MAP_PLAIN);
            }
        }).build()).get();
        Object frog = testMap.get("frog");
        Object one = testMap.get("one");
        Object two = testMap.get("two");
        Object object = testMap.get("object");

        Assert.assertTrue(frog instanceof String, "Should have found a String: " + frog);
        Assert.assertEquals(frog, "frog", "Should have found a formatted String: " + frog);
        Assert.assertTrue(object instanceof ProxySslConfig, "Should have found a ProxySslConfig: " + object);
        Assert.assertTrue(one instanceof BasicEntity, "Should have found a BasicEntity: " + one);
        Assert.assertTrue(two instanceof BasicEntity, "Should have found a BasicEntity: " + two);
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

}
