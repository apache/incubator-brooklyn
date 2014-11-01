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
package brooklyn.management.osgi;

import java.io.File;
import java.io.IOException;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.os.Os;
import brooklyn.util.osgi.Osgis;


/** 
 * Tests that OSGi entities load correctly and have the right catalog information set.
 *     
 */
public class OsgiEntitiesTest {
   
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH;
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_URL = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_URL;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws BundleException, IOException, InterruptedException {
    }
    
    /**
     * Test fix for
     * java.lang.NoClassDefFoundError: brooklyn.event.AttributeSensor not found by io.brooklyn.brooklyn-test-osgi-entities [41]
     */
    @Test
    public void testEntityProxy() throws Exception {
        File storageTempDir = Os.newTempDir("osgi-standalone");
        Framework framework = Osgis.newFrameworkStarted(storageTempDir.getAbsolutePath(), true, null);
        
        try {
        ManagementContextInternal managementContext;
        InternalEntityFactory factory;

        managementContext = new LocalManagementContextForTests();
        InternalPolicyFactory policyFactory = new InternalPolicyFactory(managementContext);
        factory = new InternalEntityFactory(managementContext, managementContext.getEntityManager().getEntityTypeRegistry(), policyFactory);

        Bundle bundle = Osgis.install(framework, BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        @SuppressWarnings("unchecked")
        Class<? extends Entity> bundleCls = (Class<? extends Entity>) bundle.loadClass("brooklyn.osgi.tests.SimpleEntityImpl");
        @SuppressWarnings("unchecked")
        Class<? extends Entity> bundleInterface = (Class<? extends Entity>) bundle.loadClass("brooklyn.osgi.tests.SimpleEntity");

        @SuppressWarnings("unchecked")
        EntitySpec<Entity> spec = (EntitySpec<Entity>) (((EntitySpec<Entity>)EntitySpec.create(bundleInterface))).impl(bundleCls);
        Entity entity = bundleCls.newInstance();
        factory.createEntityProxy(spec, entity);

        if (managementContext != null) Entities.destroyAll(managementContext);
        } finally {
            OsgiStandaloneTest.tearDownOsgiFramework(framework, storageTempDir);
        }
    }
}
