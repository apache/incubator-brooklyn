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
package org.apache.brooklyn.location.jclouds.networking;

import org.jclouds.compute.domain.NodeMetadata;
import org.apache.brooklyn.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.location.access.PortForwardManager;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Protocol;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;

public interface JcloudsPortForwarderExtension {

    /**
     * Opens port forwarding (e.g. DNAT or iptables port-forwarding) to reach the given given 
     * target port on this node (from the given cidr).
     * 
     * This should also register the port with the {@link PortForwardManager}, via 
     * {@code portForwardManager.associate(node.getId(), result, targetPort)} so that
     * subsequent calls to {@link BrooklynAccessUtils#getBrooklynAccessibleAddress(brooklyn.entity.Entity, int)}
     * will know about the mapped port.
     */
    public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr);

    public void closePortForwarding(NodeMetadata node, int targetPort, HostAndPort publicHostAndPort, Protocol protocol);
}
