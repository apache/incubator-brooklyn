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

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.Reader;
import java.io.StringReader;
import java.util.Set;

import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.RebindOptions;
import brooklyn.entity.rebind.RebindTestFixture;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class AbstractYamlRebindTest extends RebindTestFixture<StartableApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractYamlTest.class);
    protected static final String TEST_VERSION = "0.1.2";

    protected BrooklynCampPlatform platform;
    protected BrooklynCampPlatformLauncherNoServer launcher;
    private boolean forceUpdate;
    
    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        launcher = new BrooklynCampPlatformLauncherNoServer() {
            @Override
            protected LocalManagementContext newMgmtContext() {
                return (LocalManagementContext) mgmt();
            }
        };
        launcher.launch();
        platform = launcher.getCampPlatform();
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if (launcher != null) launcher.stopServers();
        }
    }

    protected StartableApplication rebind(RebindOptions options) throws Exception {
        StartableApplication result = super.rebind(options);
        if (launcher != null) {
            launcher.stopServers();
            launcher = new BrooklynCampPlatformLauncherNoServer() {
                @Override
                protected LocalManagementContext newMgmtContext() {
                    return (LocalManagementContext) mgmt();
                }
            };
            launcher.launch();
            platform = launcher.getCampPlatform();
        }
        return result;
    }
    
    @Override
    protected StartableApplication createApp() {
        return null;
    }

    protected ManagementContext mgmt() {
        return (newManagementContext != null) ? newManagementContext : origManagementContext;
    }
    
    ///////////////////////////////////////////////////
    // TODO code below is duplicate of AbstractYamlTest
    ///////////////////////////////////////////////////
    
    protected void waitForApplicationTasks(Entity app) {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(origManagementContext.getExecutionManager(), app);
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
        return createAndStartApplication(joinLines(multiLineYaml));
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
        final Entity app = origManagementContext.getEntityManager().getEntity(assembly.getId());
        getLogger().info("App - " + app);
        
        // wait for app to have started
        Set<Task<?>> tasks = origManagementContext.getExecutionManager().getTasksWithAllTags(ImmutableList.of(
                BrooklynTaskTags.EFFECTOR_TAG, 
                BrooklynTaskTags.tagForContextEntity(app), 
                BrooklynTaskTags.tagForEffectorCall(app, "start", ConfigBag.newInstance(ImmutableMap.of("locations", ImmutableMap.of())))));
        Iterables.getOnlyElement(tasks).get();
        
        return app;
    }

    protected Entity createStartWaitAndLogApplication(Reader input) throws Exception {
        Entity app = createAndStartApplication(input);
        waitForApplicationTasks(app);

        getLogger().info("App started:");
        Entities.dumpInfo(app);
        
        return app;
    }

    protected void addCatalogItems(Iterable<String> catalogYaml) {
        addCatalogItems(joinLines(catalogYaml));
    }

    protected void addCatalogItems(String... catalogYaml) {
        addCatalogItems(joinLines(catalogYaml));
    }

    protected void addCatalogItems(String catalogYaml) {
        mgmt().getCatalog().addItems(catalogYaml, forceUpdate);
    }

    protected void deleteCatalogEntity(String catalogItem) {
        mgmt().getCatalog().deleteCatalogItem(catalogItem, TEST_VERSION);
    }

    protected Logger getLogger() {
        return LOG;
    }

    private String joinLines(Iterable<String> catalogYaml) {
        return Joiner.on("\n").join(catalogYaml);
    }

    private String joinLines(String[] catalogYaml) {
        return Joiner.on("\n").join(catalogYaml);
    }

    protected String ver(String id) {
        return CatalogUtils.getVersionedId(id, TEST_VERSION);
    }

    public void forceCatalogUpdate() {
        forceUpdate = true;
    }

}
