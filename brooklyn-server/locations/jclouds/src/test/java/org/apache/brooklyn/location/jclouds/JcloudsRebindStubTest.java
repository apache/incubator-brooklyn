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
package org.apache.brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.domain.internal.HardwareImpl;
import org.jclouds.compute.domain.internal.NodeMetadataImpl;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.domain.internal.LocationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * Tests rebind (i.e. restarting Brooklyn server) when there are live JcloudsSshMachineLocation object(s).
 * 
 * It is still a live test because it connects to the Softlayer API for finding images, etc.
 * But it does not provision any VMs, so is much faster/cheaper.
 */
public class JcloudsRebindStubTest extends RebindTestFixtureWithApp {

    // TODO Duplication of AbstractJcloudsLiveTest, because we're subclassing RebindTestFixture instead.

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsRebindStubTest.class);

    public static final String SOFTLAYER_LOCATION_SPEC = "jclouds:" + AbstractJcloudsLiveTest.SOFTLAYER_PROVIDER;
    public static final String SOFTLAYER_IMAGE_ID = "UBUNTU_14_64";
    
    protected List<ManagementContext> mgmts;
    protected Multimap<ManagementContext, JcloudsSshMachineLocation> machines;
    protected BrooklynProperties brooklynProperties;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        mgmts = Lists.newCopyOnWriteArrayList(ImmutableList.<ManagementContext>of(origManagementContext));
        machines = Multimaps.synchronizedMultimap(ArrayListMultimap.<ManagementContext, JcloudsSshMachineLocation>create());
        
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = origManagementContext.getBrooklynProperties();
        AbstractJcloudsLiveTest.stripBrooklynProperties(brooklynProperties);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        for (ManagementContext mgmt : mgmts) {
            try {
                if (mgmt.isRunning()) Entities.destroyAll(mgmt);
            } catch (Exception e) {
                LOG.warn("Error destroying management context", e);
                exceptions.add(e);
            }
        }
        mgmts.clear();
        origManagementContext = null;
        newManagementContext = null;
        origApp = null;
        newApp = null;
        
        super.tearDown();
        
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error in tearDown of "+getClass(), exceptions);
        }
    }

    @Override
    protected boolean useLiveManagementContext() {
        return true;
    }

    @Override
    protected TestApplication rebind() throws Exception {
        TestApplication result = super.rebind();
        mgmts.add(newManagementContext);
        return result;
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testRebind() throws Exception {
        LocationImpl locImpl = new LocationImpl(
                LocationScope.REGION, 
                "myLocId", 
                "myLocDescription", 
                null, 
                ImmutableList.<String>of(), // iso3166Codes 
                ImmutableMap.<String,Object>of()); // metadata
        
        NodeMetadata node = new NodeMetadataImpl(
                "softlayer", 
                "myname", 
                "myid", 
                locImpl,
                URI.create("http://myuri.com"), 
                ImmutableMap.<String, String>of(), // userMetadata 
                ImmutableSet.<String>of(), // tags
                "mygroup",
                new HardwareImpl(
                        "myHardwareProviderId", 
                        "myHardwareName", 
                        "myHardwareId", 
                        locImpl, 
                        URI.create("http://myuri.com"), 
                        ImmutableMap.<String, String>of(), // userMetadata 
                        ImmutableSet.<String>of(), // tags
                        ImmutableList.<Processor>of(), 
                        1024, 
                        ImmutableList.<Volume>of(), 
                        Predicates.<Image>alwaysTrue(), // supportsImage, 
                        (String)null, // hypervisor
                        false),
                SOFTLAYER_IMAGE_ID,
                new OperatingSystem(
                        OsFamily.CENTOS, 
                        "myOsName", 
                        "myOsVersion", 
                        "myOsArch", 
                        "myDescription", 
                        true), // is64Bit
                Status.RUNNING,
                "myBackendStatus",
                22, // login-port
                ImmutableList.of("1.2.3.4"), // publicAddresses, 
                ImmutableList.of("10.2.3.4"), // privateAddresses, 
                LoginCredentials.builder().identity("myidentity").password("mypassword").build(), 
                "myHostname");
        
        ByonComputeServiceRegistry computeServiceRegistry = new ByonComputeServiceRegistry(node);
        JcloudsLocation origJcloudsLoc = (JcloudsLocation) mgmt().getLocationRegistry().resolve("jclouds:softlayer", ImmutableMap.of(
                JcloudsLocation.COMPUTE_SERVICE_REGISTRY, computeServiceRegistry,
                JcloudsLocation.WAIT_FOR_SSHABLE, false,
                JcloudsLocation.USE_JCLOUDS_SSH_INIT, false));
    
        JcloudsSshMachineLocation origMachine = (JcloudsSshMachineLocation) origJcloudsLoc.obtain(ImmutableMap.of("imageId", SOFTLAYER_IMAGE_ID));
        
        String origHostname = origMachine.getHostname();
        NodeMetadata origNode = origMachine.getNode();
        Template origTemplate = origMachine.getTemplate();

        rebind();
        
        // Check the machine is as before
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) newManagementContext.getLocationManager().getLocation(origMachine.getId());
        JcloudsLocation newJcloudsLoc = newMachine.getParent();
        String newHostname = newMachine.getHostname();
        NodeMetadata newNode = newMachine.getNode();
        Template newTemplate = newMachine.getTemplate();
        
        assertEquals(newHostname, origHostname);
        assertEquals(origNode.getId(), newNode.getId());
        
        assertEquals(newJcloudsLoc.getProvider(), origJcloudsLoc.getProvider());
    }
    
    protected static class ByonComputeServiceRegistry extends ComputeServiceRegistryImpl implements ComputeServiceRegistry {
        private final NodeMetadata node;

        ByonComputeServiceRegistry(NodeMetadata node) throws Exception {
            this.node = node;
        }

        @Override
        public ComputeService findComputeService(ConfigBag conf, boolean allowReuse) {
            ComputeService delegate = super.findComputeService(conf, allowReuse);
            return new StubComputeService(delegate, node);
        }
    }

    static class StubComputeService extends DelegatingComputeService {
        private final NodeMetadata node;
        
        public StubComputeService(ComputeService delegate, NodeMetadata node) {
            super(delegate);
            this.node = checkNotNull(node, "node");
        }
        
        @Override
        public void destroyNode(String id) {
            // no-op
        }
        
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count) throws RunNodesException {
            return ImmutableSet.of(node);
        }
        
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, Template template) throws RunNodesException {
            return ImmutableSet.of(node);
        }
        
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, TemplateOptions templateOptions) throws RunNodesException {
            return ImmutableSet.of(node);
        }
    }
}
