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
import org.apache.brooklyn.location.basic.HasSubnetHostname;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;

public interface JcloudsMachineLocation extends MachineLocation, HasSubnetHostname {
    
    @Override
    public JcloudsLocation getParent();
    
    public NodeMetadata getNode();
    
    public Template getTemplate();

    public String getJcloudsId();

    /** In most clouds, the public hostname is the only way to ensure VMs in different zones can access each other. */
    @Override
    public String getSubnetHostname();

    String getUser();

    int getPort();
}
