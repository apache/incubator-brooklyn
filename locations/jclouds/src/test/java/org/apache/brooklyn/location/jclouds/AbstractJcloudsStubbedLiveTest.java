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

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.core.util.config.ConfigBag;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.options.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * The VM creation is stubbed out, but it still requires live access (i.e. real account credentials)
 * to generate the template etc.
 * 
 * We supply a ComputeServiceRegistry that delegates to the real instance for everything except
 * VM creation and deletion. For those operations, it delegates to a NodeCreator that 
 * returns a dummy NodeMetadata, recording all calls made to it.
 */
public abstract class AbstractJcloudsStubbedLiveTest extends AbstractJcloudsLiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsStubbedLiveTest.class);

    public static final String LOCATION_SPEC = "jclouds:" + SOFTLAYER_PROVIDER + ":" + SOFTLAYER_AMS01_REGION_NAME;
    
    public static abstract class NodeCreator {
        public final List<NodeMetadata> created = Lists.newCopyOnWriteArrayList();
        public final List<String> destroyed = Lists.newCopyOnWriteArrayList();
        
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, Template template) throws RunNodesException {
            Set<NodeMetadata> result = Sets.newLinkedHashSet();
            for (int i = 0; i < count; i++) {
                NodeMetadata node = newNode(group, template);
                created.add(node);
                result.add(node);
            }
            return result;
        }
        public void destroyNode(String id) {
            destroyed.add(id);
        }
        protected abstract NodeMetadata newNode(String group, Template template);
    }
    
    public static class StubbedComputeService extends DelegatingComputeService {
        private final NodeCreator nodeCreator;
        
        public StubbedComputeService(ComputeService delegate, NodeCreator nodeCreator) {
            super(delegate);
            this.nodeCreator = nodeCreator;
        }
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, Template template) throws RunNodesException {
            return nodeCreator.createNodesInGroup(group, count, template);
        }
        @Override
        public void destroyNode(String id) {
            nodeCreator.destroyNode(id);
        }
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, TemplateOptions templateOptions) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Set<? extends NodeMetadata> destroyNodesMatching(Predicate<NodeMetadata> filter) {
            throw new UnsupportedOperationException();
        }
    }
    
    protected NodeCreator nodeCreator;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        nodeCreator = newNodeCreator();
        ComputeServiceRegistry computeServiceRegistry = new ComputeServiceRegistry() {
            @Override
            public ComputeService findComputeService(ConfigBag conf, boolean allowReuse) {
                ComputeService delegate = ComputeServiceRegistryImpl.INSTANCE.findComputeService(conf, allowReuse);
                return new StubbedComputeService(delegate, nodeCreator);
            }
        };
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(
                LOCATION_SPEC, 
                ImmutableMap.of(
                        JcloudsLocationConfig.COMPUTE_SERVICE_REGISTRY, computeServiceRegistry,
                        JcloudsLocationConfig.WAIT_FOR_SSHABLE, "false"));
    }
    
    protected abstract NodeCreator newNodeCreator();
}
