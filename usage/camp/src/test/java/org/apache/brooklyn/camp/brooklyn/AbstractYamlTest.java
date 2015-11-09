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

import java.io.Reader;
import java.io.StringReader;
import java.util.Set;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampTypePlanTransformer;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Joiner;

public abstract class AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractYamlTest.class);
    protected static final String TEST_VERSION = "0.1.2";

    private ManagementContext brooklynMgmt;
    protected BrooklynCatalog catalog;
    protected BrooklynCampPlatform platform;
    protected BrooklynCampPlatformLauncherNoServer launcher;
    private boolean forceUpdate;
    
    public AbstractYamlTest() {
        super();
    }

    protected ManagementContext mgmt() { return brooklynMgmt; }
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        forceUpdate = false;
        launcher = new BrooklynCampPlatformLauncherNoServer() {
            @Override
            protected LocalManagementContext newMgmtContext() {
                return newTestManagementContext();
            }
        };
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();
        catalog = brooklynMgmt.getCatalog();
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
        return createAndStartApplication(joinLines(multiLineYaml));
    }
    
    protected Entity createAndStartApplication(String input) throws Exception {
        return createAndStartApplication(new StringReader(input));
    }

    protected Entity createAndStartApplication(Reader input) throws Exception {
        EntitySpec<?> spec = 
            mgmt().getTypeRegistry().createSpecFromPlan(CampTypePlanTransformer.FORMAT, Streams.readFully(input), RegisteredTypeLoadingContexts.spec(Application.class), EntitySpec.class);
        final Entity app = brooklynMgmt.getEntityManager().createEntity(spec);
        // start the app (happens automatically if we use camp to instantiate, but not if we use crate spec approach)
        app.invoke(Startable.START, MutableMap.<String,String>of()).get();
        return app;
    }

    protected Entity createStartWaitAndLogApplication(Reader input) throws Exception {
        Entity app = createAndStartApplication(input);
        waitForApplicationTasks(app);

        getLogger().info("App started: "+app);
        
        return app;
    }

    protected EntitySpec<?> createAppEntitySpec(String... yaml) {
        return EntityManagementUtils.createEntitySpecForApplication(mgmt(), joinLines(yaml));
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

    protected String joinLines(Iterable<String> catalogYaml) {
        return Joiner.on("\n").join(catalogYaml);
    }

    protected String joinLines(String... catalogYaml) {
        return Joiner.on("\n").join(catalogYaml);
    }

    protected String ver(String id) {
        return CatalogUtils.getVersionedId(id, TEST_VERSION);
    }

    public void forceCatalogUpdate() {
        forceUpdate = true;
    }
}
