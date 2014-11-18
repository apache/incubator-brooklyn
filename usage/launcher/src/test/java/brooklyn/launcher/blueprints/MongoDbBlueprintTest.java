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
package brooklyn.launcher.blueprints;

import static org.testng.Assert.assertNotEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.SimpleYamlLauncherForTests;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;

public class MongoDbBlueprintTest {

    // TODO Some tests are failing! Needs investigated.
    
    private SimpleYamlLauncherForTests launcher;
    
    // The "viewer" is just for having a Brooklyn web-console available for visual inspection;
    // could be removed without affecting behaviour
    private BrooklynLauncher viewer;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        launcher = new SimpleYamlLauncherForTests();
        viewer = BrooklynLauncher.newInstance().managementContext(launcher.getManagementContext()).start();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (launcher != null) launcher.destroyAll();
        if (viewer != null) viewer.terminate();
    }
    
    @Test(groups="Integration")
    public void testMongoSharded() {
        runAppAndAssertNoFires("mongo-sharded.yaml");
    }

    @Test(groups="Integration")
    public void testMongoReplicaSet() {
        runAppAndAssertNoFires("mongo-blueprint.yaml");
    }

    @Test(groups="Integration")
    public void testMongoClientAndSingleServer() {
        runAppAndAssertNoFires("mongo-client-single-server.yaml");
    }

    @Test(groups="Integration")
    public void testMongoScripts() {
        runAppAndAssertNoFires("mongo-scripts.yaml");
    }

    @Test(groups="Integration")
    public void testMongoSingleServer() {
        runAppAndAssertNoFires("mongo-single-server-blueprint.yaml");
    }

    protected void runAppAndAssertNoFires(String yamlFile) {
        final Application app = launcher.launchAppYaml(yamlFile);

        EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity entity : Entities.descendants(app)) {
                    assertNotEquals(entity.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
                    assertNotEquals(entity.getAttribute(Attributes.SERVICE_UP), false);
                }
            }});
    }
}
