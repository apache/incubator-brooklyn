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

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.Reader;
import java.io.StringReader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Joiner;

public abstract class AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractYamlTest.class);

    private ManagementContext brooklynMgmt;
    protected BrooklynCampPlatform platform;
    protected BrooklynCampPlatformLauncherNoServer launcher;
    
    public AbstractYamlTest() {
        super();
    }

    protected ManagementContext mgmt() { return brooklynMgmt; }
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        launcher = new BrooklynCampPlatformLauncherNoServer() {
            @Override
            protected LocalManagementContext newMgmtContext() {
                return newTestManagementContext();
            }
        };
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();
        platform = launcher.getCampPlatform();
    }

    protected LocalManagementContext newTestManagementContext() {
        // TODO they don't all need osgi, just a few do, so could speed it up by specifying when they do
        return LocalManagementContextForTests.newInstanceWithOsgi();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (brooklynMgmt != null) Entities.destroyAll(brooklynMgmt);
        if (launcher != null) launcher.stopServers();
    }

    protected void waitForApplicationTasks(Entity app) {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
        getLogger().info("Waiting on " + tasks.size() + " task(s)");
        for (Task<?> t : tasks) {
            t.blockUntilEnded();
        }
    }

    protected Reader loadYaml(String yamlFileName, String ...extraLines) throws Exception {
        String input = new ResourceUtils(this).getResourceAsString(yamlFileName).trim();
        StringBuilder builder = new StringBuilder(input);
        for (String l: extraLines)
            builder.append("\n").append(l);
        return new StringReader(builder.toString());
    }
    
    protected Entity createAndStartApplication(String... multiLineYaml) throws Exception {
        return createAndStartApplication(join(multiLineYaml));
    }
    
    protected Entity createAndStartApplication(String input) throws Exception {
        return createAndStartApplication(new StringReader(input));
    }

    protected Entity createAndStartApplication(Reader input) throws Exception {
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        Assembly assembly;
        try {
            assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        } catch (Exception e) {
            getLogger().warn("Unable to instantiate " + at + " (rethrowing): " + e);
            throw e;
        }
        getLogger().info("Test - created " + assembly);
        final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
        getLogger().info("App - " + app);
        return app;
    }

    protected Entity createStartWaitAndLogApplication(Reader input) throws Exception {
        Entity app = createAndStartApplication(input);
        waitForApplicationTasks(app);

        getLogger().info("App started:");
        Entities.dumpInfo(app);
        
        return app;
    }

    protected void addCatalogItem(String... catalogYaml) {
        addCatalogItem(join(catalogYaml));
    }

    protected void addCatalogItem(String catalogYaml) {
        mgmt().getCatalog().addItem(catalogYaml);
    }

    protected void deleteCatalogEntity(String catalogItem) {
        mgmt().getCatalog().deleteCatalogItem(catalogItem);
    }
    
    protected Logger getLogger() {
        return LOG;
    }

    private String join(String[] catalogYaml) {
        return Joiner.on("\n").join(catalogYaml);
    }
    
}
