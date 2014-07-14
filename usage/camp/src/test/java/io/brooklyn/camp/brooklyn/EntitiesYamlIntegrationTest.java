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
package io.brooklyn.camp.brooklyn;

import static org.testng.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.tomcat.TomcatServer;

public class EntitiesYamlIntegrationTest extends AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntitiesYamlIntegrationTest.class);

    @Test(groups = "Integration")
    public void testStartTomcatCluster() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-tomcat-cluster.yaml"));
        waitForApplicationTasks(app);

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        final Entity entity = Iterables.getOnlyElement(app.getChildren());
        assertTrue(entity instanceof ControlledDynamicWebAppCluster, "entity="+entity);
        ControlledDynamicWebAppCluster cluster = (ControlledDynamicWebAppCluster) entity;

        assertTrue(cluster.getController() instanceof NginxController, "controller="+cluster.getController());
        Iterable<TomcatServer> tomcats = FluentIterable.from(cluster.getCluster().getMembers()).filter(TomcatServer.class);
        assertEquals(Iterables.size(tomcats), 2);
        for (TomcatServer tomcat : tomcats) {
            assertTrue(tomcat.getAttribute(TomcatServer.SERVICE_UP), "serviceup");
        }

        EntitySpec<?> spec = entity.getConfig(DynamicCluster.MEMBER_SPEC);
        assertNotNull(spec);
        assertEquals(spec.getType(), TomcatServer.class);
        assertEquals(spec.getConfig().get(DynamicCluster.QUARANTINE_FAILED_ENTITIES), Boolean.FALSE);
        assertEquals(spec.getConfig().get(DynamicCluster.INITIAL_QUORUM_SIZE), 2);
    }


    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
