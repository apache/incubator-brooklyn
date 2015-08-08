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
package brooklyn.entity.osgi.karaf;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.net.URL;
import java.util.Map;

import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableList;

public class KarafContainerTest extends BrooklynAppLiveTestSupport {

    private static final String HELLO_WORLD_JAR = "/hello-world.jar";

    LocalhostMachineProvisioningLocation localhost;
    KarafContainer karaf;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        localhost = app.newLocalhostProvisioningLocation();
    }

    // FIXME Test failing in jenkins; not sure why. The karaf log shows the mbeans never being
    // registered so we are never able to connect to them over jmx.
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdown() throws Exception {
        karaf = app.createAndManageChild(EntitySpec.create(KarafContainer.class)
                .configure("name", Identifiers.makeRandomId(8))
                .configure("displayName", "Karaf Test"));
        
        app.start(ImmutableList.of(localhost));
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, true);
        
        Entities.dumpInfo(karaf);
        final int pid = karaf.getAttribute(KarafContainer.KARAF_PID);
        Entities.submit(app, SshEffectorTasks.requirePidRunning(pid).machine(localhost.obtain())).get();
        
        karaf.stop();
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, false);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                try {
                    Assert.assertFalse(Entities.submit(app, SshEffectorTasks.isPidRunning(pid).machine(localhost.obtain())).get());
                } catch (NoMachinesAvailableException e) {
                    throw Exceptions.propagate(e);
                }
            }});
    }
    
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdownExplicitJmx() throws Exception {
        karaf = app.createAndManageChild(EntitySpec.create(KarafContainer.class)
                .configure("name", Identifiers.makeRandomId(8))
                .configure("displayName", "Karaf Test")
                .configure("rmiRegistryPort", "8099+")
                .configure("jmxPort", "9099+"));
        
        app.start(ImmutableList.of(localhost));
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, true);
        
        karaf.stop();
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, false);
    }
    
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdownLegacyJmx() throws Exception {
        karaf = app.createAndManageChild(EntitySpec.create(KarafContainer.class)
                .configure("name", Identifiers.makeRandomId(8))
                .configure("displayName", "Karaf Test")
                .configure("jmxPort", "8099+")
                .configure("rmiRegistryPort", "9099+"));
            // NB: now the above parameters have the opposite semantics to before
        
        app.start(ImmutableList.of(localhost));
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, true);
        
        karaf.stop();
        EntityTestUtils.assertAttributeEqualsEventually(karaf, Attributes.SERVICE_UP, false);
    }
    
    // FIXME Test failing in jenkins; not sure why. The karaf log shows the mbeans never being
    // registered so we are never able to connect to them over jmx.
    @Test(groups = {"Integration", "WIP"})
    public void testCanInstallAndUninstallBundle() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), HELLO_WORLD_JAR);
        URL jarUrl = getClass().getResource(HELLO_WORLD_JAR);
        assertNotNull(jarUrl);

        karaf = app.createAndManageChild(EntitySpec.create(KarafContainer.class)
            .configure("name", Identifiers.makeRandomId(8))
            .configure("displayName", "Karaf Test")
            .configure("jmxPort", "8099+")
            .configure("rmiRegistryPort", "9099+"));
        
        app.start(ImmutableList.of(localhost));
        
        long bundleId = karaf.installBundle("wrap:"+jarUrl.toString());
        
        Map<Long, Map<String,?>> bundles = karaf.listBundles();
        Map<String,?> bundle = bundles.get(bundleId);
        assertNotNull(bundle, "expected="+bundleId+"; actual="+bundles.keySet());

        // Undeploy: expect bundle to no longer be listed        
        karaf.uninstallBundle(bundleId);
        
        Map<Long, Map<String,?>> bundles2 = karaf.listBundles();
        Map<String,?> bundle2 = bundles2.get(bundleId);
        assertNull(bundle2, "expectedAbsent="+bundleId+"; actual="+bundles2.keySet());
    }
}
