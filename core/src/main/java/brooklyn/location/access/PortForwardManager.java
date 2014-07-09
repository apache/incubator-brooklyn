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

import brooklyn.location.Location;

import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;

/**
 * Records port mappings against public IP addresses with given identifiers.
 * <p>
 * To use, create a new authoritative instance (e.g. {@link PortForwardManagerAuthority}) which will live in one
 * canonical place, then set config to be a client (e.g. {@link PortForwardManagerClient} which delegates to the
 * primary instance) so the authority is shared among all communicating parties but only persisted in one place.
 * <p>
 * One Location side (e.g. a software process in a VM) can request ({@link #acquirePublicPort(String, Location, int)})
 * an unused port on a firewall / public IP address. It may then go on actually to talk to that firewall/IP to
 * provision the forwarding rule.
 * <p>
 * Subsequently the other side can use this class {@link #lookup(Location, int)} if it knows the
 * location and private port it wishes to talk to.
 * <p>
 * Implementations typically will not know anything about what the firewall/IP actually is, they just handle a
 * unique identifier for it. It is recommended, however, to {@link #recordPublicIpHostname(String, String)} an
 * accessible hostname with the identifier. This is required in order to use {@link #lookup(Location, int)}.
 */
@Beta
public interface PortForwardManager {

    /**
     * Reserves a unique public port on the given publicIpId.
     * <p>
     * Often followed by {@link #associate(String, int, Location, int)}
     * to enable {@link #lookup(Location, int)}.
     */
    public int acquirePublicPort(String publicIpId);

    /** Returns old mapping if it existed, null if it is new. */
    public PortMapping acquirePublicPortExplicit(String publicIpId, int port);

    /** Returns the port mapping for a given publicIpId and public port. */
    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort);

    /** Returns the subset of port mappings associated with a given public IP ID. */
    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId);

    /** Clears the given port mapping, returning the mapping if there was one. */
    public PortMapping forgetPortMapping(String publicIpId, int publicPort);
    
    /** @see #forgetPortMapping(String, int) */
    public boolean forgetPortMapping(PortMapping m);

    // -----------------
    
    /**
     * Records a public hostname or address to be associated with the given publicIpId for lookup purposes.
     * <p>
     * Conceivably this may have to be access-location specific.
     */
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress);

    /** Returns a recorded public hostname or address. */
    public String getPublicIpHostname(String publicIpId);
    
    /** Clears a previous call to {@link #recordPublicIpHostname(String, String)}. */
    public boolean forgetPublicIpHostname(String publicIpId);

    /**
     * Returns the public host and port for use accessing the given mapping.
     * <p>
     * Conceivably this may have to be access-location specific.
     */
    public HostAndPort getPublicHostAndPort(PortMapping m);

    // -----------------
    
    /**
     * Reserves a unique public port for the purpose of forwarding to the given target,
     * associated with a given location for subsequent lookup purpose.
     * <p>
     * If already allocated, returns the previously allocated.
     */
    public int acquirePublicPort(String publicIpId, Location l, int privatePort);

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
     * Records a location and private port against a publicIp and public port,
     * to support {@link #lookup(Location, int)}.
     * <p>
     * Superfluous if {@link #acquirePublicPort(String, Location, int)} was used,
     * but strongly recommended if {@link #acquirePublicPortExplicit(String, int)} was used
     * e.g. if the location is not known ahead of time.
     */
    public void associate(String publicIpId, int publicPort, Location l, int privatePort);

    /** Returns the subset of port mappings associated with a given location. */
    public Collection<PortMapping> getLocationPublicIpIds(Location l);
        
    /** Returns the mapping to a given private port, or null if none. */
    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort);

    /**
     * Returns true if this implementation is a client which is immutable/safe for serialization
     * i.e. it delegates to something on an entity or location elsewhere.
     */
    public boolean isClient();
    
}
