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
package org.apache.brooklyn.location.access;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.location.Location;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;

/**
 * @deprecated since 0.7.0; just use the {@link PortForwardManager}, or a direct reference to its impl {@link PortForwardManagerImpl}
 */
@Deprecated
public class PortForwardManagerClient implements PortForwardManager {

    private static final long serialVersionUID = -295204304305332895L;
    
    protected final Supplier<PortForwardManager> delegateSupplier;
    private transient volatile PortForwardManager _delegate;
    
    protected PortForwardManagerClient(Supplier<PortForwardManager> supplier) {
        this.delegateSupplier = supplier;
    }
    
    /** creates an instance given a supplier; 
     * the supplier should be brooklyn-persistable, that is to say
     * references should be in terms of entities/locations 
     * which can retrieve an authoritative source even under cloning */
    public static PortForwardManager fromSupplier(Supplier<PortForwardManager> supplier) {
        return new PortForwardManagerClient(supplier);
    }

    /** creates an instance given an entity and an interface method it implements to retrieve the PortForwardManager */ 
    public static PortForwardManager fromMethodOnEntity(final Entity entity, final String getterMethodOnEntity) {
        Preconditions.checkNotNull(entity);
        Preconditions.checkNotNull(getterMethodOnEntity);
        return new PortForwardManagerClient(new Supplier<PortForwardManager>() {
            @Override
            public PortForwardManager get() {
                PortForwardManager result;
                try {
                    result = (PortForwardManager) entity.getClass().getMethod(getterMethodOnEntity).invoke(entity);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    throw new IllegalStateException("Cannot invoke "+getterMethodOnEntity+" on "+entity+" ("+entity.getClass()+"): "+e, e);
                }
                if (result==null)
                    throw new IllegalStateException("No PortForwardManager available via "+getterMethodOnEntity+" on "+entity+" (returned null)");
                return result;
            }
        });
    }

    /** creates an instance given an entity and {@link AttributeSensor} to retrieve the PortForwardManager */ 
    public static PortForwardManager fromAttributeOnEntity(final Entity entity, final AttributeSensor<PortForwardManager> attributeOnEntity) {
        Preconditions.checkNotNull(entity);
        Preconditions.checkNotNull(attributeOnEntity);
        return new PortForwardManagerClient(new Supplier<PortForwardManager>() {
            @Override
            public PortForwardManager get() {
                PortForwardManager result = entity.getAttribute(attributeOnEntity);
                if (result==null)
                    throw new IllegalStateException("No PortForwardManager available via "+attributeOnEntity+" on "+entity+" (returned null)");
                return result;
            }
        });
    }
    
    protected PortForwardManager getDelegate() {
        if (_delegate==null) {
            _delegate = delegateSupplier.get();
        }
        return _delegate;
    }

    @Override
    public int acquirePublicPort(String publicIpId) {
        return getDelegate().acquirePublicPort(publicIpId);
    }

    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort) {
        getDelegate().associate(publicIpId, publicEndpoint, l, privatePort);
    }

    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, int privatePort) {
        getDelegate().associate(publicIpId, publicEndpoint, privatePort);
    }

    @Override
    public HostAndPort lookup(Location l, int privatePort) {
        return getDelegate().lookup(l, privatePort);
    }

    @Override
    public HostAndPort lookup(String publicIpId, int privatePort) {
        return getDelegate().lookup(publicIpId, privatePort);
    }

    @Override
    public boolean forgetPortMapping(String publicIpId, int publicPort) {
        return getDelegate().forgetPortMapping(publicIpId, publicPort);
    }

    @Override
    public boolean forgetPortMappings(Location location) {
        return getDelegate().forgetPortMappings(location);
    }

    @Override
    public boolean forgetPortMappings(String publicIpId) {
        return getDelegate().forgetPortMappings(publicIpId);
    }

    @Override
    public String getId() {
        return getDelegate().getId();
    }

    @Override
    public String getScope() {
        return getDelegate().getScope();
    }

    @Override
    public void addAssociationListener(AssociationListener listener, Predicate<? super AssociationMetadata> filter) {
        getDelegate().addAssociationListener(listener, filter);
    }

    @Override
    public void removeAssociationListener(AssociationListener listener) {
        getDelegate().removeAssociationListener(listener);
    }

    @Override
    public String toVerboseString() {
        return getClass().getName()+"[wrapping="+getDelegate().toVerboseString()+"]";
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // Deprecated
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * Reserves a unique public port for the purpose of forwarding to the given target,
     * associated with a given location for subsequent lookup purpose.
     * <p>
     * If already allocated, returns the previously allocated.
     * 
     * @deprecated since 0.7.0; use {@link #acquirePublicPort(String)}, and then use {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     */
    @Override
    @Deprecated
    public int acquirePublicPort(String publicIpId, Location l, int privatePort) {
        return getDelegate().acquirePublicPort(publicIpId, l, privatePort);
    }

    /** 
     * Returns old mapping if it existed, null if it is new.
     * 
     * @deprecated since 0.7.0; use {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     */
    @Override
    @Deprecated
    public PortMapping acquirePublicPortExplicit(String publicIpId, int publicPort) {
        return getDelegate().acquirePublicPortExplicit(publicIpId, publicPort);
    }

    /**
     * Records a location and private port against a publicIp and public port,
     * to support {@link #lookup(Location, int)}.
     * <p>
     * Superfluous if {@link #acquirePublicPort(String, Location, int)} was used,
     * but strongly recommended if {@link #acquirePublicPortExplicit(String, int)} was used
     * e.g. if the location is not known ahead of time.
     * 
     * @deprecated Use {@link #associate(String, HostAndPort, Location, int)}
     */
    @Override
    @Deprecated
    public void associate(String publicIpId, int publicPort, Location l, int privatePort) {
        getDelegate().associate(publicIpId, publicPort, l, privatePort);
    }

    /**
     * Records a public hostname or address to be associated with the given publicIpId for lookup purposes.
     * <p>
     * Conceivably this may have to be access-location specific.
     * 
     * @deprecated Use {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     */
    @Override
    @Deprecated
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress) {
        getDelegate().recordPublicIpHostname(publicIpId, hostnameOrPublicIpAddress);
    }

    /**
     * Returns a recorded public hostname or address.
     * 
     * @deprecated Use {@link #lookup(String, int)} or {@link #lookup(Location, int)}
     */
    @Override
    @Deprecated
    public String getPublicIpHostname(String publicIpId) {
        return getDelegate().getPublicIpHostname(publicIpId);
    }
    
    /**
     * Clears a previous call to {@link #recordPublicIpHostname(String, String)}.
     * 
     * @deprecated Use {@link #forgetPortMapping(String, int)} or {@link #forgetPortMapping(Location, int)}
     */
    @Override
    @Deprecated
    public boolean forgetPublicIpHostname(String publicIpId) {
        return getDelegate().forgetPublicIpHostname(publicIpId);
    }

    @Override
    @Deprecated
    public boolean isClient() {
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////
    // Deprecated; just internal
    ///////////////////////////////////////////////////////////////////////////////////

    /** 
     * Returns the port mapping for a given publicIpId and public port.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort) {
        return getDelegate().getPortMappingWithPublicSide(publicIpId, publicPort);
    }

    /** 
     * Returns the subset of port mappings associated with a given public IP ID.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId) {
        return getDelegate().getPortMappingWithPublicIpId(publicIpId);
    }

    /** 
     * @see #forgetPortMapping(String, int)
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public boolean forgetPortMapping(PortMapping m) {
        return getDelegate().forgetPortMapping(m);
    }

    /**
     * Returns the public host and port for use accessing the given mapping.
     * <p>
     * Conceivably this may have to be access-location specific.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public HostAndPort getPublicHostAndPort(PortMapping m) {
        return getDelegate().getPublicHostAndPort(m);
    }

    /** 
     * Returns the subset of port mappings associated with a given location.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public Collection<PortMapping> getLocationPublicIpIds(Location l) {
        return getDelegate().getLocationPublicIpIds(l);
    }
        
    /** 
     * Returns the mapping to a given private port, or null if none.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Override
    @Deprecated
    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort) {
        return getDelegate().getPortMappingWithPrivateSide(l, privatePort);
    }
    
    @Override
    public String toString() {
        return getClass().getName()+"[id="+getId()+"]";
    }

    @Override
    public String getDisplayName() {
        return getDelegate().getDisplayName();
    }

    @Override
    public Location getParent() {
        return getDelegate().getParent();
    }

    @Override
    public Collection<Location> getChildren() {
        return getDelegate().getChildren();
    }

    @Override
    public void setParent(Location newParent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsLocation(Location potentialDescendent) {
        return getDelegate().containsLocation(potentialDescendent);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return getDelegate().getConfig(key);
    }

    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        return getDelegate().getConfig(key);
    }

    @Override
    public boolean hasConfig(ConfigKey<?> key, boolean includeInherited) {
        return getDelegate().hasConfig(key, includeInherited);
    }

    @Override
    public Map<String, Object> getAllConfig(boolean includeInherited) {
        return getDelegate().getAllConfig(includeInherited);
    }

    @Override
    public boolean hasExtension(Class<?> extensionType) {
        return getDelegate().hasExtension(extensionType);
    }

    @Override
    public <T> T getExtension(Class<T> extensionType) {
        return getDelegate().getExtension(extensionType);
    }

    @Override
    public String getCatalogItemId() {
        return getDelegate().getCatalogItemId();
    }

    @Override
    public TagSupport tags() {
        return getDelegate().tags();
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        return getDelegate().setConfig(key, val);
    }

    @Override
    public ConfigurationSupport config() {
        return getDelegate().config();
    }
}
