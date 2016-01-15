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
package org.apache.brooklyn.entity.java;

import static org.testng.Assert.assertTrue;

import java.io.File;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.java.VanillaJavaApp;
import org.apache.brooklyn.entity.java.VanillaJavaAppImpl;
import org.apache.brooklyn.entity.java.JavaOptsTest.TestingJavaOptsVanillaJavaAppImpl;
import org.apache.brooklyn.policy.enricher.RollingTimeWindowMeanEnricher;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class VanillaJavaAppRebindTest {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaJavaAppRebindTest.class);
    
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
        app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        loc = app.newLocalhostProvisioningLocation(MutableMap.of("address", "localhost"));
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

        app.start(ImmutableList.of(loc));

        rebind();
        VanillaJavaApp javaProcess2 = (VanillaJavaApp) Iterables.find(app.getChildren(), Predicates.instanceOf(VanillaJavaApp.class));
        
        EntityTestUtils.assertAttributeEqualsEventually(javaProcess2, VanillaJavaApp.SERVICE_UP, true);
    }

    @Test(groups="Integration")
    public void testRebindToKilledJavaApp() throws Exception {
        VanillaJavaApp javaProcess = app.addChild(EntitySpec.create(VanillaJavaApp.class, TestingJavaOptsVanillaJavaAppImpl.class)
            .configure("main", MAIN_CLASS.getCanonicalName()).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH)));

        app.start(ImmutableList.of(loc));
        javaProcess.kill();
        
        long starttime = System.currentTimeMillis();
        rebind();
        long rebindTime = System.currentTimeMillis() - starttime;
        
        VanillaJavaApp javaProcess2 = (VanillaJavaApp) Iterables.find(app.getChildren(), Predicates.instanceOf(VanillaJavaApp.class));
        EntityTestUtils.assertAttributeEqualsEventually(javaProcess2, VanillaJavaApp.SERVICE_UP, false);
        
        // check that it was quick (previously it hung)
        assertTrue(rebindTime < 30*1000, "rebindTime="+rebindTime);
    }
    
    
    @Test(groups="Integration")
    public void testEnrichersOnRebindJavaApp() throws Exception {
        VanillaJavaApp javaProcess = app.addChild(EntitySpec.create(VanillaJavaApp.class, EnrichedVanillaJavaAppImpl.class)
            .configure("main", MAIN_CLASS.getCanonicalName()).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH)));

        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEventuallyNonNull(javaProcess, EnrichedVanillaJavaAppImpl.AVG1);
        EntityTestUtils.assertAttributeEventuallyNonNull(javaProcess, EnrichedVanillaJavaAppImpl.AVG2);
        LOG.info("Got avg "+javaProcess.getAttribute(EnrichedVanillaJavaAppImpl.AVG1));

        rebind();
        VanillaJavaApp javaProcess2 = (VanillaJavaApp) Iterables.find(app.getChildren(), Predicates.instanceOf(VanillaJavaApp.class));

        // check sensors working
        EntityTestUtils.assertAttributeChangesEventually(javaProcess2, EnrichedVanillaJavaAppImpl.PROCESS_CPU_TIME); 
        LOG.info("Avg now "+javaProcess2.getAttribute(EnrichedVanillaJavaAppImpl.AVG1));
        
        // check enrichers are functioning
        EntityTestUtils.assertAttributeChangesEventually(javaProcess2, EnrichedVanillaJavaAppImpl.AVG1); 
        EntityTestUtils.assertAttributeChangesEventually(javaProcess2, EnrichedVanillaJavaAppImpl.AVG2);
        LOG.info("Avg now "+javaProcess2.getAttribute(EnrichedVanillaJavaAppImpl.AVG1));
        
        // and check we don't have too many
        Assert.assertEquals(javaProcess2.getEnrichers().size(), javaProcess.getEnrichers().size());
    }

    public static class EnrichedVanillaJavaAppImpl extends VanillaJavaAppImpl {
        private static final AttributeSensor<Double> AVG1 = Sensors.newDoubleSensor("avg1");
        private static final AttributeSensor<Double> AVG2 = Sensors.newDoubleSensor("avg2");
        
        @Override
        public void onManagementStarted() {
            super.onManagementStarted();
            LOG.info("mgmt started for "+this);
            enrichers().add(new RollingTimeWindowMeanEnricher<Double>(this, PROCESS_CPU_TIME, AVG1, Duration.TEN_SECONDS));
        }
        @Override
        protected void connectSensors() {
            super.connectSensors();
            LOG.info("connecting sensors for "+this);
            enrichers().add(new RollingTimeWindowMeanEnricher<Double>(this, PROCESS_CPU_TIME, AVG2, Duration.TEN_SECONDS));
        }
    }

}
