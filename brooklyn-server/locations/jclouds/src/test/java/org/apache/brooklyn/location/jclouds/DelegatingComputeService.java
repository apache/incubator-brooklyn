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

import java.util.Map;
import java.util.Set;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.extensions.ImageExtension;
import org.jclouds.compute.extensions.SecurityGroupExtension;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Location;
import org.jclouds.scriptbuilder.domain.Statement;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.ListenableFuture;

public class DelegatingComputeService implements ComputeService {

    private final ComputeService delegate;
    
    public DelegatingComputeService(ComputeService delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public ComputeServiceContext getContext() {
        return delegate.getContext();
    }

    @Override
    public TemplateBuilder templateBuilder() {
        return delegate.templateBuilder();
    }

    @Override
    public TemplateOptions templateOptions() {
        return delegate.templateOptions();
    }

    @Override
    public Set<? extends Hardware> listHardwareProfiles() {
        return delegate.listHardwareProfiles();
    }

    @Override
    public Set<? extends Image> listImages() {
        return delegate.listImages();
    }

    @Override
    public Image getImage(String id) {
        return delegate.getImage(id);
    }

    @Override
    public Set<? extends ComputeMetadata> listNodes() {
        return delegate.listNodes();
    }

    @Override
    public Set<? extends NodeMetadata> listNodesByIds(Iterable<String> ids) {
        return delegate.listNodesByIds(ids);
    }

    @Override
    public Set<? extends Location> listAssignableLocations() {
        return delegate.listAssignableLocations();
    }

    @Override
    public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, Template template) throws RunNodesException {
        return delegate.createNodesInGroup(group, count, template);
    }

    @Override
    public Set<? extends NodeMetadata> createNodesInGroup(String group, int count, TemplateOptions templateOptions)
            throws RunNodesException {
        return delegate.createNodesInGroup(group, count, templateOptions);
    }

    @Override
    public Set<? extends NodeMetadata> createNodesInGroup(String group, int count) throws RunNodesException {
        return delegate.createNodesInGroup(group, count);
    }

    @Override
    public void resumeNode(String id) {
        delegate.resumeNode(id);
    }

    @Override
    public Set<? extends NodeMetadata> resumeNodesMatching(Predicate<NodeMetadata> filter) {
        return delegate.resumeNodesMatching(filter);
    }

    @Override
    public void suspendNode(String id) {
        delegate.suspendNode(id);
    }

    @Override
    public Set<? extends NodeMetadata> suspendNodesMatching(Predicate<NodeMetadata> filter) {
        return delegate.suspendNodesMatching(filter);
    }

    @Override
    public void destroyNode(String id) {
        delegate.destroyNode(id);
    }

    @Override
    public Set<? extends NodeMetadata> destroyNodesMatching(Predicate<NodeMetadata> filter) {
        return delegate.destroyNodesMatching(filter);
    }

    @Override
    public void rebootNode(String id) {
        delegate.rebootNode(id);
    }

    @Override
    public Set<? extends NodeMetadata> rebootNodesMatching(Predicate<NodeMetadata> filter) {
        return delegate.rebootNodesMatching(filter);
    }

    @Override
    public NodeMetadata getNodeMetadata(String id) {
        return delegate.getNodeMetadata(id);
    }

    @Override
    public Set<? extends NodeMetadata> listNodesDetailsMatching(Predicate<ComputeMetadata> filter) {
        return delegate.listNodesDetailsMatching(filter);
    }

    @Override
    public Map<? extends NodeMetadata, ExecResponse> runScriptOnNodesMatching(Predicate<NodeMetadata> filter, String runScript)
            throws RunScriptOnNodesException {
        return delegate.runScriptOnNodesMatching(filter, runScript);
    }

    @Override
    public Map<? extends NodeMetadata, ExecResponse> runScriptOnNodesMatching(Predicate<NodeMetadata> filter, Statement runScript)
            throws RunScriptOnNodesException {
        return delegate.runScriptOnNodesMatching(filter, runScript);
    }

    @Override
    public Map<? extends NodeMetadata, ExecResponse> runScriptOnNodesMatching(Predicate<NodeMetadata> filter,
            String runScript, RunScriptOptions options) throws RunScriptOnNodesException {
        return delegate.runScriptOnNodesMatching(filter, runScript, options);
    }

    @Override
    public Map<? extends NodeMetadata, ExecResponse> runScriptOnNodesMatching(Predicate<NodeMetadata> filter,
            Statement runScript, RunScriptOptions options) throws RunScriptOnNodesException {
        return delegate.runScriptOnNodesMatching(filter, runScript, options);
    }

    @Override
    public ExecResponse runScriptOnNode(String id, Statement runScript, RunScriptOptions options) {
        return delegate.runScriptOnNode(id, runScript, options);
    }

    @Override
    public ListenableFuture<ExecResponse> submitScriptOnNode(String id, String runScript, RunScriptOptions options) {
        return delegate.submitScriptOnNode(id, runScript, options);
    }

    @Override
    public ListenableFuture<ExecResponse> submitScriptOnNode(String id, Statement runScript, RunScriptOptions options) {
        return delegate.submitScriptOnNode(id, runScript, options);
    }

    @Override
    public ExecResponse runScriptOnNode(String id, Statement runScript) {
        return delegate.runScriptOnNode(id, runScript);
    }

    @Override
    public ExecResponse runScriptOnNode(String id, String runScript, RunScriptOptions options) {
        return delegate.runScriptOnNode(id, runScript, options);
    }

    @Override
    public ExecResponse runScriptOnNode(String id, String runScript) {
        return delegate.runScriptOnNode(id, runScript);
    }

    @Override
    public Optional<ImageExtension> getImageExtension() {
        return delegate.getImageExtension();
    }

    @Override
    public Optional<SecurityGroupExtension> getSecurityGroupExtension() {
        return delegate.getSecurityGroupExtension();
    }

}
