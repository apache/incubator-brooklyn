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
package brooklyn.entity.java;

import static org.testng.Assert.assertTrue;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.JavaOptsTest.TestingJavaOptsVanillaJavaAppImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class VanillaJavaAppRebindTest {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaJavaAppRebindTest.class);
    
    private static final long TIMEOUT_MS = 10*1000;

    private static String BROOKLYN_THIS_CLASSPATH = null;
    private static Class<?> MAIN_CLASS = ExampleVanillaMain.class;

    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext managementContext;
    private File mementoDir;
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        
        if (BROOKLYN_THIS_CLASSPATH==null) {
            BROOKLYN_THIS_CLASSPATH = ResourceUtils.create(MAIN_CLASS).getClassLoaderDir();
        }
        app = new TestApplicationImpl();
        loc = new LocalhostMachineProvisioningLocation(MutableMap.of("address", "localhost"));
        Entities.startManagement(app, managementContext);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    private void rebind() throws Exception {
        RebindTestUtils.waitForPersisted(app);
        managementContext.terminate();
        
        app = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        managementContext = (LocalManagementContext) app.getManagementContext();
        loc = (LocalhostMachineProvisioningLocation) Iterables.get(app.getLocations(), 0, null);
    }
    
    @Test(groups="Integration")
    public void testRebindToJavaApp() throws Exception {
        VanillaJavaApp javaProcess = app.addChild(EntitySpec.create(VanillaJavaApp.class, TestingJavaOptsVanillaJavaAppImpl.class)
            .configure("main", MAIN_CLASS.getCanonicalName()).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH)));

        Entities.manage(javaProcess);
        app.start(ImmutableList.of(loc));

        rebind();
        VanillaJavaApp javaProcess2 = (VanillaJavaApp) Iterables.find(app.getChildren(), Predicates.instanceOf(VanillaJavaApp.class));
        
        EntityTestUtils.assertAttributeEqualsEventually(javaProcess2, VanillaJavaApp.SERVICE_UP, true);
    }

    @Test(groups="Integration")
    public void testRebindToKilledJavaApp() throws Exception {
        VanillaJavaApp javaProcess = app.addChild(EntitySpec.create(VanillaJavaApp.class, TestingJavaOptsVanillaJavaAppImpl.class)
            .configure("main", MAIN_CLASS.getCanonicalName()).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH)));
        Entities.manage(javaProcess);
        app.start(ImmutableList.of(loc));
        javaProcess.kill();
        
        long starttime = System.currentTimeMillis();
        rebind();
        long rebindTime = System.currentTimeMillis() - starttime;
        
        VanillaJavaApp javaProcess2 = (VanillaJavaApp) Iterables.find(app.getChildren(), Predicates.instanceOf(VanillaJavaApp.class));
        EntityTestUtils.assertAttributeEqualsEventually(javaProcess2, VanillaJavaApp.SERVICE_UP, false);
        
        // check that it was quick (previously it hung for 
        assertTrue(rebindTime < 30*1000, "rebindTime="+rebindTime);
    }
}
