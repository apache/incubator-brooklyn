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
package org.apache.brooklyn.launcher.blueprints;

import static org.testng.Assert.assertNotEquals;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;

import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.rebind.RebindOptions;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.launcher.SimpleYamlLauncherForTests;
import org.apache.brooklyn.launcher.camp.BrooklynCampPlatformLauncher;
import org.apache.brooklyn.management.ManagementContext;

import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.util.ResourceUtils;
import brooklyn.util.os.Os;

public abstract class AbstractBlueprintTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBlueprintTest.class);
    
    protected File mementoDir;
    protected ClassLoader classLoader = AbstractBlueprintTest.class.getClassLoader();
    
    protected ManagementContext mgmt;
    protected SimpleYamlLauncherForTests launcher;
    protected BrooklynLauncher viewer;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Os.newTempDir(getClass());
        mgmt = createOrigManagementContext();
        LOG.info("Test "+getClass()+" persisting to "+mementoDir);

        launcher = new SimpleYamlLauncherForTests() {
            @Override
            protected BrooklynCampPlatformLauncherAbstract newPlatformLauncher() {
                return new BrooklynCampPlatformLauncher() {
                    protected ManagementContext newManagementContext() {
                        return AbstractBlueprintTest.this.mgmt;
                    }
                };
            }
        };
        viewer = BrooklynLauncher.newInstance()
                .managementContext(mgmt)
                .start();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) {
                for (Application app: mgmt.getApplications()) {
                    LOG.debug("destroying app "+app+" (managed? "+Entities.isManaged(app)+"; mgmt is "+mgmt+")");
                    try {
                        Entities.destroy(app);
                        LOG.debug("destroyed app "+app+"; mgmt now "+mgmt);
                    } catch (Exception e) {
                        LOG.error("problems destroying app "+app, e);
                    }
                }
            }
            if (launcher != null) launcher.destroyAll();
            if (viewer != null) viewer.terminate();
            if (mgmt != null) Entities.destroyAll(mgmt);
            if (mementoDir != null) FileBasedObjectStore.deleteCompletely(mementoDir);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
            launcher = null;
        }
    }

    protected void runTest(String yamlFile) throws Exception {
        final Application app = launcher.launchAppYaml(yamlFile);
        
        assertNoFires(app);
        
        Application newApp = rebind();
        assertNoFires(newApp);
    }
    
    protected void runTest(Reader yaml) throws Exception {
        final Application app = launcher.launchAppYaml(yaml);
        
        assertNoFires(app);
        
        Application newApp = rebind();
        assertNoFires(newApp);
    }
    
    protected void assertNoFires(final Entity app) {
        EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity entity : Entities.descendants(app)) {
                    assertNotEquals(entity.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
                    assertNotEquals(entity.getAttribute(Attributes.SERVICE_UP), false);
                    
                    if (entity instanceof SoftwareProcess) {
                        EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
                        EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_UP, Boolean.TRUE);
                    }
                }
            }});
    }

    protected Reader loadYaml(String url, String location) {
        String yaml = 
                "location: "+location+"\n"+
                new ResourceUtils(this).getResourceAsString(url);
        return new StringReader(yaml);
    }
    
    
    //////////////////////////////////////////////////////////////////
    // FOR REBIND                                                   //
    // See brooklyn.entity.rebind.RebindTestFixture in core's tests //
    //////////////////////////////////////////////////////////////////

    /** rebinds, and sets newApp */
    protected Application rebind() throws Exception {
        return rebind(RebindOptions.create());
    }

    protected Application rebind(RebindOptions options) throws Exception {
        ManagementContext origMgmt = mgmt;
        ManagementContext newMgmt = createNewManagementContext();
        Collection<Application> origApps = origMgmt.getApplications();
        
        options = RebindOptions.create(options);
        if (options.classLoader == null) options.classLoader(classLoader);
        if (options.mementoDir == null) options.mementoDir(mementoDir);
        if (options.origManagementContext == null) options.origManagementContext(origMgmt);
        if (options.newManagementContext == null) options.newManagementContext(newMgmt);
        
        for (Application origApp : origApps) {
            RebindTestUtils.waitForPersisted(origApp);
        }
        
        mgmt = options.newManagementContext;
        Application newApp = RebindTestUtils.rebind(options);
        return newApp;
    }
    
    /** @return A started management context */
    protected LocalManagementContext createOrigManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(1)
                .forLive(true)
                .emptyCatalog(true)
                .buildStarted();
    }

    /** @return An unstarted management context */
    protected LocalManagementContext createNewManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(1)
                .forLive(true)
                .emptyCatalog(true)
                .buildUnstarted();
    }
}
