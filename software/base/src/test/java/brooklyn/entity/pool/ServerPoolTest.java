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
package brooklyn.entity.pool;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ServerPoolTest extends AbstractServerPoolTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolTest.class);

    @Test
    public void testAppCanBeDeployedToServerPool() {
        TestApplication app = createAppWithChildren(1);
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app.getAttribute(Attributes.SERVICE_UP));
        for (Entity child : app.getChildren()) {
            assertTrue(child.getAttribute(Attributes.SERVICE_UP));
        }
    }

    @Test
    public void testFailureWhenNotEnoughServersAvailable() {
        TestApplication app = createAppWithChildren(getInitialPoolSize() + 1);
        assertNoMachinesAvailableForApp(app);
        // Not asserting attr = true because the sensor will probably be null
        assertFalse(Boolean.TRUE.equals(app.getAttribute(Attributes.SERVICE_UP)));
    }

    @Test
    public void testDeployReleaseDeploy() {
        TestApplication app = createAppWithChildren(getInitialPoolSize());
        TestApplication app2 = createAppWithChildren(1);

        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app.getAttribute(Attributes.SERVICE_UP));
        assertAvailableCountEquals(0);
        assertNoMachinesAvailableForApp(app2);

        app.stop();
        assertFalse(app.getAttribute(Attributes.SERVICE_UP));
        assertAvailableCountEquals(getInitialPoolSize());

        app2.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app2.getAttribute(Attributes.SERVICE_UP));
        
        assertAvailableCountEventuallyEquals(getInitialPoolSize() - 1);
        assertClaimedCountEquals(1);
    }

    @Test
    public void testResizingPoolUp() {
        TestApplication app = createAppWithChildren(getInitialPoolSize());
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app.getAttribute(Attributes.SERVICE_UP));

        TestApplication app2 = createAppWithChildren(1);
        assertNoMachinesAvailableForApp(app2);

        pool.resizeByDelta(1);
        
        assertAvailableCountEventuallyEquals(1);

        assertEquals((int) pool.getCurrentSize(), getInitialPoolSize() + 1);
        app2.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app2.getAttribute(Attributes.SERVICE_UP));
    }

    @Test
    public void testResizePoolDownSucceedsWhenEnoughMachinesAreFree() {
        TestApplication app = createAppWithChildren(1);
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertAvailableCountEquals(getInitialPoolSize() - 1);

        pool.resize(1);

        assertAvailableCountEventuallyEquals(0);
    }

    @Test
    public void testResizeDownDoesNotReleaseClaimedMachines() {
        TestApplication app = createAppWithChildren(getInitialPoolSize() - 1);
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertAvailableCountEquals(1);
        assertClaimedCountEquals(getInitialPoolSize() - 1);

        LOG.info("Test attempting to resize to 0 members. Should only drop one machine.");
        pool.resize(0);

        assertAvailableCountEventuallyEquals(0);

        assertEquals(Iterables.size(pool.getMembers()), getInitialPoolSize() - 1);
        assertAvailableCountEquals(0);
        assertClaimedCountEquals(getInitialPoolSize() - 1);
    }

}
