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
import java.util.Set;

import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.Networking;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.callables.RunScriptOnNode;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

public class JcloudsWinRmMachineLocation extends WinRmMachineLocation implements JcloudsMachineLocation {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsWinRmMachineLocation.class);

    @SetFromFlag
    JcloudsLocation jcloudsParent;
    
    /**
     * @deprecated since 0.9.0; the node should not be persisted.
     */
    @SetFromFlag
    @Deprecated
    NodeMetadata node;
    
    /**
     * @deprecated since 0.9.0; the template should not be persisted.
     */
    @SetFromFlag
    @Deprecated
    Template template;
    
    @SetFromFlag
    String nodeId;

    @SetFromFlag
    String imageId;

    @SetFromFlag
    Set<String> privateAddresses;
    
    @SetFromFlag
    Set<String> publicAddresses;

    @SetFromFlag
    String hostname;

    /**
     * Historically, "node" and "template" were persisted. However that is a very bad idea!
     * It means we pull in lots of jclouds classes into the persisted state. We are at an  
     * intermediate stage, where we want to handle rebinding to old state that has "node"
     * and new state that should not. We therefore leave in the {@code @SetFromFlag} on node
     * so that we read it back, but we ensure the value is null when we write it out!
     * 
     * TODO We will rename these to get rid of the ugly underscore when the old node/template 
     * fields are deleted.
     */
    private transient Optional<NodeMetadata> _node;

    private transient Optional<Template> _template;

    private transient Optional<Image> _image;

    private transient String _privateHostname;
    
    public JcloudsWinRmMachineLocation() {
    }

    @Override
    public void init() {
        if (jcloudsParent != null) {
            super.init();
            if (node != null) {
                setNode(node);
            }
            if (template != null) {
                setTemplate(template);
            }
        } else {
            // TODO Need to fix the rebind-detection, and not call init() on rebind.
            // This will all change when locations become entities.
            if (LOG.isDebugEnabled()) LOG.debug("Not doing init() of {} because parent not set; presuming rebinding", this);
        }
    }
    
    @Override
    public void rebind() {
        super.rebind();
        
        if (node != null) {
            setNode(node);
            node = null;
        }

        if (template != null) {
            setTemplate(template);
            template = null;
        }
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("user", getUser())
                .add("address", getAddress())
                .add("port", getPort())
                .add("node", _node)
                .add("nodeId", getJcloudsId())
                .add("imageId", getImageId())
                .add("privateAddresses", getPrivateAddresses())
                .add("publicAddresses", getPublicAddresses())
                .add("parentLocation", getParent())
                .add("osDetails", getOsDetails())
                .toString();
    }

    protected void setNode(NodeMetadata node) {
        this.node = null;
        nodeId = node.getId();
        imageId = node.getImageId();
        privateAddresses = node.getPrivateAddresses();
        publicAddresses = node.getPublicAddresses();
        hostname = node.getHostname();
        _node = Optional.of(node);
    }

    protected void setTemplate(Template template) {
        this.template = null;
        _template = Optional.of(template);
        _image = Optional.fromNullable(template.getImage());
    }

    @Override
    public int getPort() {
        return getConfig(WINRM_PORT);
    }
    
    @Override
    public JcloudsLocation getParent() {
        return jcloudsParent;
    }
    
    @Override
    public Optional<NodeMetadata> getOptionalNode() {
      if (_node == null) {
          _node = Optional.fromNullable(getParent().getComputeService().getNodeMetadata(nodeId));
      }
      return _node;
    }

    @VisibleForTesting
    Optional<NodeMetadata> peekNode() {
        return _node;
    }

    protected Optional<Image> getOptionalImage() {
        if (_image == null) {
            _image = Optional.fromNullable(getParent().getComputeService().getImage(imageId));
        }
        return _image;
    }

    /**
     * @since 0.9.0
     * @deprecated since 0.9.0 (only added as aid until the deprecated {@link #getTemplate()} is deleted)
     */
    @Deprecated
    protected Optional<Template> getOptionalTemplate() {
        if (_template == null) {
            _template = Optional.absent();
        }
        return _template;
    }

    /**
     * @deprecated since 0.9.0
     */
    @Override
    @Deprecated
    public NodeMetadata getNode() {
        Optional<NodeMetadata> result = getOptionalNode();
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalStateException("Node "+nodeId+" not present in "+getParent());
        }
    }

    /**
     * @deprecated since 0.9.0
     */
    @Override
    @Deprecated
    public Template getTemplate() {
        Optional<Template> result = getOptionalTemplate();
        if (result.isPresent()) {
            String msg = "Deprecated use of getTemplate() for "+this;
            LOG.warn(msg + " - see debug log for stacktrace");
            LOG.debug(msg, new Exception("for stacktrace"));
            return result.get();
        } else {
            throw new IllegalStateException("Template for "+nodeId+" (in "+getParent()+") not cached (deprecated use of getTemplate())");
        }
    }

    @Override
    public String getHostname() {
        if (hostname != null) {
            return hostname;
        }
        InetAddress address = getAddress();
        return (address != null) ? address.getHostAddress() : null;
    }


    /** In clouds like AWS, the public hostname is the only way to ensure VMs in different zones can access each other. */
    @Override
    public Set<String> getPublicAddresses() {
        return (publicAddresses == null) ? ImmutableSet.<String>of() : publicAddresses;
    }
    
    @Override
    public Set<String> getPrivateAddresses() {
        return (privateAddresses == null) ? ImmutableSet.<String>of() : privateAddresses;
    }


    @Override
    public String getSubnetHostname() {
        // Same impl as JcloudsSshMachineLocation
        if (_privateHostname == null) {
            for (String p : getPrivateAddresses()) {
                if (Networking.isLocalOnly(p)) continue;
                _privateHostname = p;
            }
            if (groovyTruth(getPublicAddresses())) {
                _privateHostname = getPublicAddresses().iterator().next();
            } else if (groovyTruth(getHostname())) {
                _privateHostname = getHostname();
            } else {
                return null;
            }
        }
        return _privateHostname;
    }

    @Override
    public String getSubnetIp() {
        // Same impl as JcloudsSshMachineLocation
        Optional<String> privateAddress = getPrivateAddress();
        if (privateAddress.isPresent()) {
            return privateAddress.get();
        }
        if (groovyTruth(node.getPublicAddresses())) {
            return node.getPublicAddresses().iterator().next();
        }
        return null;
    }

    protected Optional<String> getPrivateAddress() {
        // Same impl as JcloudsSshMachineLocation
        for (String p : getPrivateAddresses()) {
            if (Networking.isLocalOnly(p)) continue;
            return Optional.of(p);
        }
        return Optional.absent();
    }
    
    @Override
    public String getJcloudsId() {
        return nodeId;
    }
    
    protected String getImageId() {
        return imageId;
    }
}
