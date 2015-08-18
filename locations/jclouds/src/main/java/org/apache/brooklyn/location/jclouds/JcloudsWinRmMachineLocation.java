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

import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Set;

import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.Networking;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;

public class JcloudsWinRmMachineLocation extends WinRmMachineLocation implements JcloudsMachineLocation {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsWinRmMachineLocation.class);

    @SetFromFlag
    JcloudsLocation jcloudsParent;
    
    @SetFromFlag
    NodeMetadata node;
    
    @SetFromFlag
    Template template;

    public JcloudsWinRmMachineLocation() {
    }

    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("user", getUser())
                .add("address", getAddress())
                .add("port", getPort())
                .add("node", getNode())
                .add("jcloudsId", getJcloudsId())
                .add("privateAddresses", node.getPrivateAddresses())
                .add("publicAddresses", node.getPublicAddresses())
                .add("parentLocation", getParent())
                .add("osDetails", getOsDetails())
                .toString();
    }

    @Override
    public int getPort() {
        return getConfig(WINRM_PORT);
    }
    
    @Override
    public NodeMetadata getNode() {
        return node;
    }
    
    @Override
    public Template getTemplate() {
        return template;
    }
    
    @Override
    public JcloudsLocation getParent() {
        return jcloudsParent;
    }
    
    @Override
    public String getHostname() {
        InetAddress address = getAddress();
        return (address != null) ? address.getHostAddress() : null;
    }
    
    @Override
    public Set<String> getPublicAddresses() {
        return node.getPublicAddresses();
    }
    
    @Override
    public Set<String> getPrivateAddresses() {
        return node.getPrivateAddresses();
    }

    @Override
    public String getSubnetHostname() {
        // TODO: TEMP FIX: WAS:
        // String publicHostname = jcloudsParent.getPublicHostname(node, Optional.<HostAndPort>absent(), config().getBag());
        // but this causes a call to JcloudsUtil.getFirstReachableAddress, which searches for accessible SSH service.
        // This workaround is good for public nodes but not private-subnet ones.
        return getHostname();
    }

    @Override
    public String getSubnetIp() {
        Optional<String> privateAddress = getPrivateAddress();
        if (privateAddress.isPresent()) {
            return privateAddress.get();
        }

        String hostname = jcloudsParent.getPublicHostname(node, Optional.<HostAndPort>absent(), config().getBag());
        if (hostname != null && !Networking.isValidIp4(hostname)) {
            try {
                return InetAddress.getByName(hostname).getHostAddress();
            } catch (UnknownHostException e) {
                LOG.debug("Cannot resolve IP for hostname {} of machine {} (so returning hostname): {}", new Object[] {hostname, this, e});
            }
        }
        return hostname;
    }

    protected Optional<String> getPrivateAddress() {
        if (groovyTruth(node.getPrivateAddresses())) {
            Iterator<String> pi = node.getPrivateAddresses().iterator();
            while (pi.hasNext()) {
                String p = pi.next();
                // disallow local only addresses
                if (Networking.isLocalOnly(p)) continue;
                // other things may be public or private, but either way, return it
                return Optional.of(p);
            }
        }
        return Optional.absent();
    }
    
    @Override
    public String getJcloudsId() {
        return node.getId();
    }
}
