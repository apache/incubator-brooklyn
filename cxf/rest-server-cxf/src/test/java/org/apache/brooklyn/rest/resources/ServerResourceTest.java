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
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.BrooklynVersion;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcessDriver;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcessImpl;
import org.apache.brooklyn.rest.domain.HighAvailabilitySummary;
import org.apache.brooklyn.rest.domain.VersionSummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import javax.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;

@Test(singleThreaded = true,
        // by using a different suite name we disallow interleaving other tests between the methods of this test class, which wrecks the test fixtures
        suiteName = "ServerResourceTest")
public class ServerResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ServerResourceTest.class);
    
    @Test
    public void testGetVersion() throws Exception {
        VersionSummary version = client().path("/server/version").get(VersionSummary.class);
        assertEquals(version.getVersion(), BrooklynVersion.get());
    }

    @Test
    public void testGetStatus() throws Exception {
        String status = client().path("/server/status").get(String.class);
        assertEquals(status, "MASTER");
    }

    @Test
    public void testGetHighAvailability() throws Exception {
        // Note by default management context from super is started without HA enabled.
        // Therefore can only assert a minimal amount of stuff.
        HighAvailabilitySummary summary = client().path("/server/highAvailability").get(HighAvailabilitySummary.class);
        log.info("HA summary is: "+summary);
        
        String ownNodeId = getManagementContext().getManagementNodeId();
        assertEquals(summary.getOwnId(), ownNodeId);
        assertEquals(summary.getMasterId(), ownNodeId);
        assertEquals(summary.getNodes().keySet(), ImmutableSet.of(ownNodeId));
        assertEquals(summary.getNodes().get(ownNodeId).getNodeId(), ownNodeId);
        assertEquals(summary.getNodes().get(ownNodeId).getStatus(), "MASTER");
        assertNotNull(summary.getNodes().get(ownNodeId).getLocalTimestamp());
        // remote will also be non-null if there is no remote backend (local is re-used)
        assertNotNull(summary.getNodes().get(ownNodeId).getRemoteTimestamp());
        assertEquals(summary.getNodes().get(ownNodeId).getLocalTimestamp(), summary.getNodes().get(ownNodeId).getRemoteTimestamp());
    }

    @SuppressWarnings("serial")
    @Test
    public void testReloadsBrooklynProperties() throws Exception {
        final AtomicInteger reloadCount = new AtomicInteger();
        getManagementContext().addPropertiesReloadListener(new ManagementContext.PropertiesReloadListener() {
            @Override public void reloaded() {
                reloadCount.incrementAndGet();
            }});
        client().path("/server/properties/reload").post(null);
        assertEquals(reloadCount.get(), 1);
    }

    @Test
    void testGetConfig() throws Exception {
        ((ManagementContextInternal)getManagementContext()).getBrooklynProperties().put("foo.bar.baz", "quux");
        try {
            assertEquals(client().path("/server/config/foo.bar.baz").get(String.class), "quux");
        } finally {
            ((ManagementContextInternal)getManagementContext()).getBrooklynProperties().remove("foo.bar.baz");
        }
    }

    @Test
    void testGetMissingConfigThrowsException() throws Exception {
        final String key = "foo.bar.baz";
        BrooklynProperties properties = ((ManagementContextInternal)getManagementContext()).getBrooklynProperties();
        Object existingValue = null;
        boolean keyAlreadyPresent = false;
        if (properties.containsKey(key)) {
            existingValue = properties.remove(key);
            keyAlreadyPresent = true;
        }
        try {
            final WebClient webClient = client().path("/server/config/" + key);
            Response response = webClient.get();
            assertEquals(response.getStatus(), 204);
        } finally {
            if (keyAlreadyPresent) {
                properties.put(key, existingValue);
            }
        }
    }

    // Alternatively could reuse a blocking location, see org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest.ReleaseLatchLocation
    @ImplementedBy(StopLatchEntityImpl.class)
    public interface StopLatchEntity extends EmptySoftwareProcess {
        public void unblock();
        public boolean isBlocked();
    }

    public static class StopLatchEntityImpl extends EmptySoftwareProcessImpl implements StopLatchEntity {
        private CountDownLatch lock = new CountDownLatch(1);
        private volatile boolean isBlocked;

        @Override
        public void unblock() {
            lock.countDown();
        }

        @Override
        protected void postStop() {
            super.preStop();
            try {
                isBlocked = true;
                lock.await();
                isBlocked = false;
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
        }

        @Override
        public Class<?> getDriverInterface() {
            return EmptySoftwareProcessDriver.class;
        }

        @Override
        public boolean isBlocked() {
            return isBlocked;
        }

    }

}
