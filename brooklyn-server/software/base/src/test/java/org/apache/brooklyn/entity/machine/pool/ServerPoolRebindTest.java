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
package org.apache.brooklyn.entity.machine.pool;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Collection;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.machine.pool.ServerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class ServerPoolRebindTest extends AbstractServerPoolTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolRebindTest.class);
    private ClassLoader classLoader = getClass().getClassLoader();
    private File mementoDir;

    @Override
    protected ManagementContext createManagementContext() {
        mementoDir = Files.createTempDir();
        return RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        super.tearDown();
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    private Collection<Application> rebind(TestApplication app) throws Exception {
        LOG.info("Rebind start");
        RebindTestUtils.waitForPersisted(app);
        ((LocalManagementContext) app.getManagementContext()).terminate();
        Collection<Application> r = RebindTestUtils.rebindAll(RebindOptions.create().mementoDir(mementoDir).classLoader(classLoader));
        LOG.info("Rebind complete");
        return r;
    }

    @Test(enabled = false)
    public void testRebindingToPool() throws Exception {
        TestApplication app = createAppWithChildren(1);
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app.getAttribute(Attributes.SERVICE_UP));
        assertAvailableCountEventuallyEquals(pool, getInitialPoolSize() - 1);
        assertClaimedCountEventuallyEquals(pool, 1);

        Collection<Application> reboundApps = rebind(poolApp);
        ServerPool reboundPool = null;
        for (Application reboundApp : reboundApps) {
            Optional<Entity> np = Iterables.tryFind(reboundApp.getChildren(), Predicates.instanceOf(ServerPool.class));
            if (np.isPresent()) {
                mgmt = reboundApp.getManagementContext();
                reboundPool = (ServerPool) np.get();
                break;
            }
        }

        assertNotNull(reboundPool, "No app in rebound context has " + ServerPool.class.getName() +
                " child. Apps: " + reboundApps);
        assertNotNull(reboundPool.getDynamicLocation());
        assertTrue(reboundPool.getAttribute(Attributes.SERVICE_UP));
        assertAvailableCountEventuallyEquals(reboundPool, getInitialPoolSize() - 1);
        assertClaimedCountEventuallyEquals(reboundPool, 1);

        TestApplication app2 = createAppWithChildren(1);
        app2.start(ImmutableList.of(reboundPool.getDynamicLocation()));
        assertTrue(app2.getAttribute(Attributes.SERVICE_UP));
        assertAvailableCountEventuallyEquals(reboundPool, getInitialPoolSize() - 2);
        assertClaimedCountEventuallyEquals(reboundPool, 2);

    }

}
