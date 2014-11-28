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
package brooklyn.location.access;

import java.util.Collection;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;

import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;

/**
 * Acts as a registry for existing port mappings (e.g. the public endpoints for accessing specific
 * ports on private VMs). This could be using DNAT, or iptables port-forwarding, or Docker port-mapping 
 * via the host, or any other port mapping approach.
 * 
 * Also controls the allocation of ports via {@link #acquirePublicPort(String)}
 * (e.g. for port-mapping with DNAT, then which port to use for the public side).
 * 
 * Implementations typically will not know anything about what the firewall/IP actually is, they just 
 * handle a unique identifier for it.
 * 
 * To use, see {@link PortForwardManagerLocationResolver}, with code such as 
 * {@code managementContext.getLocationRegistry().resolve("portForwardManager(scope=global)")}.
 * 
 * @see PortForwardManagerImpl for implementation notes and considerations.
 */
@Beta
public interface PortForwardManager extends Location {

    /**
     * The intention is that there is one PortForwardManager instance per "scope". If you 
     * use global, then it will be a shared instance (for that management context). If you 
     * pass in your own name (e.g. "docker-fjie3") then it will shared with just any other
     * places that use that same location spec (e.g. {@code portForwardManager(scope=docker-fjie3)}).
     */
    public static final ConfigKey<String> SCOPE = ConfigKeys.newStringConfigKey(
            "scope",
            "The scope that this applies to, defaulting to global",
            "global");

    @Beta
    public static final ConfigKey<Integer> PORT_FORWARD_MANAGER_STARTING_PORT = ConfigKeys.newIntegerConfigKey(
            "brooklyn.portForwardManager.startingPort",
            "The starting port for assigning port numbers, such as for DNAT",
            11000);

    public String getScope();

    /**
     * Reserves a unique public port on the given publicIpId.
     * <p>
     * Often followed by {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     * to enable {@link #lookup(String, int)} or {@link #lookup(Location, int)} respectively.
     */
    public int acquirePublicPort(String publicIpId);

    /**
     * Records a location and private port against a public endpoint (ip and port),
     * to support {@link #lookup(Location, int)}.
     * <p>
     * Superfluous if {@link #acquirePublicPort(String, Location, int)} was used,
     * but strongly recommended if {@link #acquirePublicPortExplicit(String, int)} was used
     * e.g. if the location is not known ahead of time.
     */
    public void associate(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort);

    /**
     * Records a mapping for publicIpId:privatePort to a public endpoint, such that it can
     * subsequently be looked up using {@link #lookup(String, int)}.
     */
    public void associate(String publicIpId, HostAndPort publicEndpoint, int privatePort);
    
    /**
     * Returns the public ip hostname and public port for use contacting the given endpoint.
     * <p>
     * Will return null if:
     * <ul>
     * <li>No publicPort is associated with this location and private port.
     * <li>No publicIpId is associated with this location and private port.
     * <li>No publicIpHostname is recorded against the associated publicIpId.
     * </ul>
     * Conceivably this may have to be access-location specific.
     *
     * @see #recordPublicIpHostname(String, String)
     */
    public HostAndPort lookup(Location l, int privatePort);

    /**
     * Returns the public endpoint (host and port) for use contacting the given endpoint.
     * 
     * Expects a previous call to {@link #associate(String, HostAndPort, int)}, to register
     * the endpoint.
     * 
     * Will return null if there has not been a public endpoint associated with this pairing.
     */
    public HostAndPort lookup(String publicIpId, int privatePort);

    /** 
     * Clears the given port mapping, returning true if there was a match.
     */
    public boolean forgetPortMapping(String publicIpId, int publicPort);
    
    /** 
     * Clears the port mappings associated with the given location, returning true if there were any matches.
     */
    public boolean forgetPortMappings(Location location);
    
    public String toVerboseString();

    
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
    @Deprecated
    public int acquirePublicPort(String publicIpId, Location l, int privatePort);

    /** 
     * Returns old mapping if it existed, null if it is new.
     * 
     * @deprecated since 0.7.0; use {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     */
    @Deprecated
    public PortMapping acquirePublicPortExplicit(String publicIpId, int port);

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
    @Deprecated
    public void associate(String publicIpId, int publicPort, Location l, int privatePort);

    /**
     * Records a public hostname or address to be associated with the given publicIpId for lookup purposes.
     * <p>
     * Conceivably this may have to be access-location specific.
     * 
     * @deprecated Use {@link #associate(String, HostAndPort, int)} or {@link #associate(String, HostAndPort, Location, int)}
     */
    @Deprecated
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress);

    /**
     * Returns a recorded public hostname or address.
     * 
     * @deprecated Use {@link #lookup(String, int)} or {@link #lookup(Location, int)}
     */
    @Deprecated
    public String getPublicIpHostname(String publicIpId);
    
    /**
     * Clears a previous call to {@link #recordPublicIpHostname(String, String)}.
     * 
     * @deprecated Use {@link #forgetPortMapping(String, int)} or {@link #forgetPortMappings(Location)}
     */
    @Deprecated
    public boolean forgetPublicIpHostname(String publicIpId);

    /**
     * Returns true if this implementation is a client which is immutable/safe for serialization
     * i.e. it delegates to something on an entity or location elsewhere.
     * 
     * @deprecated since 0.7.0; no need to separate client-proxy from impl
     */
    @Deprecated
    public boolean isClient();
    

    ///////////////////////////////////////////////////////////////////////////////////
    // Deprecated; just internal
    ///////////////////////////////////////////////////////////////////////////////////

    /** 
     * Returns the port mapping for a given publicIpId and public port.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort);

    /** 
     * Returns the subset of port mappings associated with a given public IP ID.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId);

    /** 
     * @see {@link #forgetPortMapping(String, int)} and {@link #forgetPortMappings(Location)}
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public boolean forgetPortMapping(PortMapping m);

    /**
     * Returns the public host and port for use accessing the given mapping.
     * <p>
     * Conceivably this may have to be access-location specific.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public HostAndPort getPublicHostAndPort(PortMapping m);

    /** 
     * Returns the subset of port mappings associated with a given location.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public Collection<PortMapping> getLocationPublicIpIds(Location l);
        
    /** 
     * Returns the mapping to a given private port, or null if none.
     * 
     * @deprecated since 0.7.0; this method will be internal only
     */
    @Deprecated
    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort);
}
