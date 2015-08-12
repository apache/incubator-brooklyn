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
package org.apache.brooklyn.qa.brooklynnode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.basic.BrooklynObjectInternal.ConfigurationSupportInternal;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.brooklynnode.BrooklynEntityMirror;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNode.DeployBlueprintEffector;
import brooklyn.entity.proxying.EntitySpec;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.management.ManagementContext;

import brooklyn.location.Location;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.maven.MavenRetriever;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Tests obtaining a machine with a private IP only. For the machine to be
 * accessible we should have a gateway machine already running in the same
 * network.
 *
 * Starts a BrooklynNode with a public IP and on it starts two machines -
 * one with public and one with private only IP.
 *
 * The test lives here so it has access to the dist archive.
 */
public class SoftlayerObtainPrivateLiveTest {

    // Expects that the location is already configured in brooklyn.properties
    private static final String LOCATION_SPEC = "jclouds:aws-ec2";


    private static final Logger log = LoggerFactory.getLogger(SoftlayerObtainPrivateLiveTest.class);

    private static final ImmutableMap<String, Duration> TIMEOUT = ImmutableMap.of("timeout", Duration.ONE_HOUR);
    // Should this be a black list instead?
    private Set<String> LOCATION_CONFIG_WHITE_LIST = ImmutableSet.of(
            JcloudsLocationConfig.CLOUD_REGION_ID.getName(),
            JcloudsLocationConfig.ACCESS_IDENTITY.getName(),
            JcloudsLocationConfig.ACCESS_CREDENTIAL.getName(),
            JcloudsLocationConfig.IMAGE_ID.getName(),
            JcloudsLocationConfig.HARDWARE_ID.getName(),
            JcloudsLocationConfig.TEMPLATE_OPTIONS.getName());

    private static final String NAMED_LOCATION_PREFIX = "brooklyn.location.named.";
    private static final String TEST_LOCATION = "test-location";
    private static final String TEST_LOCATION_PRIVATE = TEST_LOCATION + "-private";
    private static final String TEST_LOCATION_PUBLIC = TEST_LOCATION + "-public";

    private BrooklynLauncher launcher;
    private ManagementContext mgmt;
    private TestApplication app;
    private Location loc;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = LocalManagementContextForTests.builder(true)
                .useDefaultProperties()
                .build();
        launcher = BrooklynLauncher
                .newInstance()
                .managementContext(mgmt)
                .start();
        app = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        mgmt.getEntityManager().manage(app);
        loc = createLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        Entities.destroyAll(mgmt);
        launcher.terminate();
    }

    private Location createLocation() {
        return mgmt.getLocationRegistry().resolve(LOCATION_SPEC);
    }

    @Test(groups="Live")
    public void testObtain() {
        String localUrl = MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-dist", "tar.gz", "dist"));
        String userName = "admin";
        String userPassword = Strings.makeRandomId(6);
        String remoteConfig = Joiner.on('\n').join(MutableList.of(
                "brooklyn.webconsole.security.users=" + userName,
                "brooklyn.webconsole.security.user.admin.password=" + userPassword)
                .appendAll(getLocationConfig())
                .append("\n"));

        log.info("Using distribution {}", localUrl);
        log.info("Remote credentials are {}:{}", userName, userPassword);
        log.info("Remote config \n{}", remoteConfig);

        EntitySpec<BrooklynNode> nodeSpec = EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.DISTRO_UPLOAD_URL, localUrl)
                .configure(BrooklynNode.MANAGEMENT_USER, userName)
                .configure(BrooklynNode.MANAGEMENT_PASSWORD, userPassword)
                .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS, remoteConfig);

        BrooklynNode node = app.createAndManageChild(nodeSpec);
        app.start(ImmutableList.of(loc));
        try {
            // TODO Assumes that the second-level machines will be in the same private network as the BrooklynNode machine.
            //      The private network id can be set explicitly in templateOptions.primaryBackendNetworkComponentNetworkVlanId.
            BrooklynEntityMirror publicApp = deployTestApp(node, true);
            BrooklynEntityMirror privateApp = deployTestApp(node, false);

            EntityTestUtils.assertAttributeEventually(TIMEOUT, publicApp, ServiceStateLogic.SERVICE_STATE_ACTUAL,
                    Predicates.in(ImmutableList.of(Lifecycle.RUNNING, Lifecycle.ON_FIRE)));
            EntityTestUtils.assertAttributeEventually(TIMEOUT, privateApp, ServiceStateLogic.SERVICE_STATE_ACTUAL,
                    Predicates.in(ImmutableList.of(Lifecycle.RUNNING, Lifecycle.ON_FIRE)));

            EntityTestUtils.assertAttributeEquals(publicApp, ServiceStateLogic.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
            EntityTestUtils.assertAttributeEquals(privateApp, ServiceStateLogic.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

            EntityTestUtils.assertAttributeEqualsEventually(publicApp, Attributes.SERVICE_UP, Boolean.TRUE);
            EntityTestUtils.assertAttributeEqualsEventually(privateApp, Attributes.SERVICE_UP, Boolean.TRUE);
        } finally {
            node.invoke(BrooklynNode.STOP_NODE_AND_KILL_APPS, ImmutableMap.<String, String>of()).getUnchecked();
        }
    }

    private BrooklynEntityMirror deployTestApp(BrooklynNode node, boolean hasPublicNetwork) {
        String entityId = node.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ImmutableMap.of(DeployBlueprintEffector.BLUEPRINT_CAMP_PLAN.getName(), getBlueprintPlan(hasPublicNetwork))).getUnchecked();
        return node.addChild(EntitySpec.create(BrooklynEntityMirror.class)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, entityId)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, node.getAttribute(BrooklynNode.WEB_CONSOLE_URI).toString() + "/v1/applications/"+entityId+"/entities/"+entityId));
    }

    private Collection<String> getLocationConfig() {
        Map<String, Object> config = MutableMap.copyOf(((ConfigurationSupportInternal)loc.config()).getBag().getAllConfig());
        config.putAll(customizeSharedLocation());
        return MutableList.<String>of()
                .appendAll(createLocationConfig(NAMED_LOCATION_PREFIX + TEST_LOCATION, (String)config.get("spec.original"), config))
                .appendAll(createLocationConfig(NAMED_LOCATION_PREFIX + TEST_LOCATION_PUBLIC, "named:" + TEST_LOCATION, customizePublicLocation()))
                .appendAll(createLocationConfig(NAMED_LOCATION_PREFIX + TEST_LOCATION_PRIVATE, "named:" + TEST_LOCATION, customizePrivateLocation()));
    }

    private Collection<String> createLocationConfig(String prefix, String parent, Map<String, ?> config) {
        return MutableList.<String>of()
                .append(prefix + "=" + parent)
                .appendAll(locationConfigToProperties(prefix, config));
    }

    protected Collection<String> locationConfigToProperties(String prefix, Map<String, ?> config) {
        Collection<String> loc = new ArrayList<String>();
        for (String key : config.keySet()) {
            if (LOCATION_CONFIG_WHITE_LIST.contains(key)) {
                loc.add(prefix + "." + key + "=" + config.get(key));
            }
        }
        return loc;
    }

    protected Map<String, String> customizeSharedLocation() {
        return ImmutableMap.of();
    }

    protected Map<String, String> customizePublicLocation() {
        return ImmutableMap.of();
    }

    protected Map<String, String> customizePrivateLocation() {
        return ImmutableMap.<String, String>of(
                "templateOptions", "{privateNetworkOnlyFlag: true}");
    }

    protected String getBlueprintPlan(boolean hasPublicNetwork) {
        return Joiner.on('\n').join(ImmutableList.of(
                "location: " + getTestLocation(hasPublicNetwork),
                "services:",
                "- type: brooklyn.entity.machine.MachineEntity",
                "  name: " + (hasPublicNetwork ? "Public" : "Private")
                ));
    }

    private static String getTestLocation(boolean hasPublicNetwork) {
        return hasPublicNetwork ? TEST_LOCATION_PUBLIC : TEST_LOCATION_PRIVATE;
    }

}
