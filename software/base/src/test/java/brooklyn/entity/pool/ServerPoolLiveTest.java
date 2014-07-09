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

import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

public class ServerPoolLiveTest extends AbstractServerPoolTest {

    public static final String PROVIDER = "softlayer";

    protected BrooklynProperties brooklynProperties;

    @Override
    protected Location createLocation() {
        // Image: {id=CENTOS_6_64, providerId=CENTOS_6_64, os={family=centos, version=6.5, description=CentOS / CentOS / 6.5-64 LAMP for Bare Metal, is64Bit=true}, description=CENTOS_6_64, status=AVAILABLE, loginUser=root}
        Map<String, ?> allFlags = MutableMap.<String, Object>builder()
                .put("provider", PROVIDER)
                .put("tags", ImmutableList.of(getClass().getName()))
                .put("vmNameMaxLength", 30)
                .put("imageId", "CENTOS_6_64")
                .build();
        return mgmt.getLocationRegistry().resolve(PROVIDER, allFlags);
    }

    @Override
    protected ManagementContext createManagementContext() {
        String[] propsToRemove = new String[]{"imageId", "imageDescriptionRegex", "imageNameRegex", "inboundPorts", "hardwareId", "minRam"};

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String propToRemove : propsToRemove) {
            for (String propVariant : ImmutableList.of(propToRemove, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propToRemove))) {
                brooklynProperties.remove("brooklyn.locations.jclouds." + PROVIDER + "." + propVariant);
                brooklynProperties.remove("brooklyn.locations." + propVariant);
                brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + "." + propVariant);
                brooklynProperties.remove("brooklyn.jclouds." + propVariant);
            }
        }

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        return new LocalManagementContextForTests(brooklynProperties);
    }

    @Override
    protected int getInitialPoolSize() {
        return 1;
    }

    @Test(groups = "Live")
    public void testAppCanBeDeployedToPool() {
        TestApplication app = createAppWithChildren(1);
        app.start(ImmutableList.of(pool.getDynamicLocation()));
        assertTrue(app.getAttribute(Attributes.SERVICE_UP));
        for (Entity child : app.getChildren()) {
            assertTrue(child.getAttribute(Attributes.SERVICE_UP));
        }
        TestApplication app2 = createAppWithChildren(1);
        assertNoMachinesAvailableForApp(app2);
    }

}
