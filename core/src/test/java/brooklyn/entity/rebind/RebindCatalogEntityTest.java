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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;

import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.Sensors;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.javalang.UrlClassLoader;

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

    private static final String JAR_PATH = "brooklyn/entity/rebind/brooklyn-AppInCatalog.jar";
    private static final String APP_CLASSNAME = "brooklyn.entity.rebind.AppInCatalog";

    private URL url;

    @Override
    protected StartableApplication createApp() {
        // do nothing here
        return null;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        url = getClass().getClassLoader().getResource(JAR_PATH);
        assertNotNull(url, "Could not find on classpath: "+JAR_PATH);
        super.setUp();
    }

    // TODO Fails with an NPE trying to use:
    //      managementContext.getCatalog().addToClasspath(url.toString())
    //      classLoader = origManagementContext.getCatalog().getRootClassLoader();
    //      appClazz = (Class<? extends AbstractApplication>) classLoader.loadClass(APP_CLASSNAME);
    //
    // Note: to test before/after behaviour (i.e. that we're really fixing what we think we are) then comment out the body of:
    //       AbstractMemento.injectTypeClass(Class)
    @Test
    public void testRestoresAppFromCatalogClassloader() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends AbstractApplication> appClazz = (Class<? extends AbstractApplication>) new UrlClassLoader(url).loadClass(APP_CLASSNAME);
        origManagementContext.getCatalog().addItem(appClazz);
        
        EntitySpec<StartableApplication> appSpec = EntitySpec.create(StartableApplication.class, appClazz)
                .configure("myconf", "myconfval");
        origApp = ApplicationBuilder.newManagedApp(appSpec, origManagementContext);
        ((EntityInternal)origApp).setAttribute(Sensors.newStringSensor("mysensor"), "mysensorval");
        
        newApp = rebind();
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
        testRestoresAppFromCatalogClassloader();
    }
    
    // TODO Not using RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    //      because that won't have right catalog classpath.
    //      How to reuse that code cleanly?
    protected StartableApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);

        LocalManagementContext newManagementContext = RebindTestUtils.newPersistingManagementContextUnstarted(mementoDir, classLoader);
        
        @SuppressWarnings("unchecked")
        Class<? extends AbstractApplication> appClazz = (Class<? extends AbstractApplication>) new UrlClassLoader(url).loadClass(APP_CLASSNAME);
        newManagementContext.getCatalog().addItem(appClazz);
        
        ClassLoader classLoader = newManagementContext.getCatalog().getRootClassLoader();
        List<Application> newApps = newManagementContext.getRebindManager().rebind(classLoader);
        newManagementContext.getRebindManager().start();
        return (StartableApplication) newApps.get(0);
    }

}
