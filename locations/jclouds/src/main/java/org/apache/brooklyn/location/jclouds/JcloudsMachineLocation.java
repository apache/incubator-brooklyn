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

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.location.HasSubnetHostname;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;

import com.google.common.base.Optional;

public interface JcloudsMachineLocation extends MachineLocation, HasSubnetHostname {
    
    @Override
    public JcloudsLocation getParent();
    
    public Optional<NodeMetadata> getOptionalNode();

    /**
     * @deprecated since 0.9.0; instead use {@link #getOptionalNode()}. After rebind, the node will 
     *             not be available if the VM is no longer running.
     * 
     * @throws IllegalStateException If the node is not available (i.e. not cached, and cannot be  
     *         found from cloud provider).
     */
    @Deprecated
    public NodeMetadata getNode();
    
    /**
     * @deprecated since 0.9.0; instead use {@link #getOptionalNode()}. After rebind, the node will not
     * be available if the VM is no longer running.
     */
    @Deprecated
    public Template getTemplate();

    public String getJcloudsId();

    /** In most clouds, the public hostname is the only way to ensure VMs in different zones can access each other. */
    @Override
    public String getSubnetHostname();

    String getUser();

    int getPort();
}
