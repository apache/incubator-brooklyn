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
package org.apache.brooklyn.core.mgmt.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;

import java.io.Closeable;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.core.catalog.internal.CatalogInitialization;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.util.core.javalang.UrlClassLoader;

import com.google.common.base.Function;

public class RebindCatalogEntityTest extends RebindTestFixture<StartableApplication> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RebindCatalogEntityTest.class);

    /*
     * Code contained in brooklyn-AppInCatalog.jar is:
     * 
     * package brooklyn.entity.rebind;
     * public class AppInCatalog extends AbstractApplication {
     *     public static final ConfigKey<String> MY_CONF = ConfigKeys.newStringConfigKey("myconf");
     *     public static final AttributeSensor<String> MY_SENSOR = Sensors.newStringSensor("mysensor");
     * }
     */

    private static final String JAR_PATH = "/brooklyn/entity/rebind/brooklyn-AppInCatalog.jar";
    private static final String APP_CLASSNAME = "brooklyn.entity.rebind.AppInCatalog";

    private URL url;

    @Override
    protected boolean useEmptyCatalog() {
        return true;
    }

    @Override
    protected StartableApplication createApp() {
        // do nothing here
        return null;
    }
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), JAR_PATH);
        url = getClass().getResource(JAR_PATH);
        assertNotNull(url, "Could not find on classpath: "+JAR_PATH);
        super.setUp();
    }

    // TODO Failed in jenkins (once on 20141104, with invocationCount=100): mysensor was null post-rebind.
    //
    // Note: to test before/after behaviour (i.e. that we're really fixing what we think we are) then comment out the body of:
    //       AbstractMemento.injectTypeClass(Class)
    //
    // NB: this behaviour is generally deprecated in favour of OSGi now.
    // @Test FIXME [BROOKLYN-162] 
    public void testRestoresAppFromCatalogClassloader() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends AbstractApplication> appClazz = (Class<? extends AbstractApplication>) new UrlClassLoader(url).loadClass(APP_CLASSNAME);
        origManagementContext.getCatalog().addItem(appClazz);
        
        EntitySpec<StartableApplication> appSpec = EntitySpec.create(StartableApplication.class, appClazz)
                .configure("myconf", "myconfval");
        origApp = ApplicationBuilder.newManagedApp(appSpec, origManagementContext);
        ((EntityInternal)origApp).setAttribute(Sensors.newStringSensor("mysensor"), "mysensorval");
        
        newApp = rebindWithAppClass();
        Entities.dumpInfo(newApp);
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
        assertEquals(newApp.getClass().getName(), APP_CLASSNAME);
        assertEquals(newApp.getEntityType().getName(), APP_CLASSNAME);
        assertEquals(newApp.getAttribute(Sensors.newStringSensor("mysensor")), "mysensorval");
        assertEquals(newApp.getConfig(ConfigKeys.newStringConfigKey("myconf")), "myconfval");
    }
    
    @Test(invocationCount=100, groups="Integration")
    public void testRestoresAppFromCatalogClassloaderManyTimes() throws Exception {
        // FIXME [BROOKLYN-162]
    	// Need to fix package name and rebuild brooklyn-AppInCatalog.jar
    	//  or better add it as a new test-bundles subproject
    	// testRestoresAppFromCatalogClassloader();
    }
    
    // TODO Not using RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    //      because that won't have right catalog classpath.
    //      How to reuse that code cleanly?
    protected StartableApplication rebindWithAppClass() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        LocalManagementContext newManagementContext = RebindTestUtils.newPersistingManagementContextUnstarted(mementoDir, classLoader);

        UrlClassLoader ucl = new UrlClassLoader(url);
        @SuppressWarnings("unchecked")
        final Class<? extends AbstractApplication> appClazz = (Class<? extends AbstractApplication>) ucl.loadClass(APP_CLASSNAME);
        // ucl.close is only introduced in java 1.7
        if (ucl instanceof Closeable) ((Closeable)ucl).close();

        newManagementContext.getCatalogInitialization().addPopulationCallback(new Function<CatalogInitialization, Void>() {
            @Override
            public Void apply(CatalogInitialization input) {
                input.getManagementContext().getCatalog().addItem(appClazz);
                return null;
            }
        });
        
        ClassLoader classLoader = newManagementContext.getCatalog().getRootClassLoader();
        
        classLoader.loadClass(appClazz.getName());
        List<Application> newApps = newManagementContext.getRebindManager().rebind(classLoader, null, ManagementNodeState.MASTER);
        newManagementContext.getRebindManager().startPersistence();
        return (StartableApplication) newApps.get(0);
    }

}
