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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.rebind.BasicLocationRebindSupport;
import brooklyn.entity.rebind.RebindContext;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.collections.MutableMap;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

@SuppressWarnings("serial")
public class PortForwardManagerImpl extends AbstractLocation implements PortForwardManager {

    // TODO This implementation is not efficient, and currently has a cap of about 50000 rules.
    // Need to improve the efficiency and scale.
    // A quick win could be to use a different portReserved counter for each publicIpId,
    // when calling acquirePublicPort?
    
    // TODO Callers need to be more careful in acquirePublicPort for which ports are actually in use.
    // If multiple apps sharing the same public-ip (e.g. in the same vcloud-director vOrg) then they 
    // must not allocate the same public port (so can share the same PortForwardManagerAuthority
    // via PortForwardManager.Factory.sharedInstance).
    // However, this still doesn't check if the port is *actually* available. For example, if a
    // different Brooklyn instance is also deploying there then we can get port conflicts, or if 
    // some ports in that range are already in use (e.g. due to earlier dev/test runs) then this
    // will not be respected. Callers should probably figure out the port number themselves, but
    // that also leads to concurrency issues.

    // TODO The publicIpId means different things to different callers:
    //  - In acquirePublicPort() it is (often?) an identifier of the actual public ip.
    //  - In later calls to associate(), it is (often?) an identifier for the target machine
    //    such as the jcloudsMachine.getJcloudsId().
    
    // TODO Should PortForwardManager be changed to extend AbstractLocation, or is this wrapper ok?
    
    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerImpl.class);
    
    public static final ConfigKey<String> SCOPE = ConfigKeys.newStringConfigKey(
            "scope",
            "The scope that this applies to, defaulting to global",
            "global");

    @Beta
    public static final ConfigKey<Integer> PORT_FORWARD_MANAGER_STARTING_PORT = ConfigKeys.newIntegerConfigKey(
            "brooklyn.portForwardManager.startingPort",
            "The starting port for assigning port numbers, such as for DNAT",
            11000);
    
    protected final Map<String,PortMapping> mappings = new LinkedHashMap<String,PortMapping>();
    
    @Deprecated
    protected final Map<String,String> publicIpIdToHostname = new LinkedHashMap<String,String>();
    
    // horrible hack -- see javadoc above
    private final AtomicInteger portReserved = new AtomicInteger(11000);

    public PortForwardManagerImpl() {
        super();
        if (isLegacyConstruction()) {
            log.warn("Deprecated construction of "+PortForwardManagerImpl.class.getName()+"; instead use location resolver");
        }
    }
    
    @Override
    public void init() {
        super.init();
        Integer portStartingPoint;
        Object rawPort = getAllConfigBag().getStringKey(PORT_FORWARD_MANAGER_STARTING_PORT.getName());
        if (rawPort != null) {
            portStartingPoint = getConfig(PORT_FORWARD_MANAGER_STARTING_PORT);
        } else {
            portStartingPoint = getManagementContext().getConfig().getConfig(PORT_FORWARD_MANAGER_STARTING_PORT);
        }
        portReserved.set(portStartingPoint);
        log.debug(this+" set initial port to "+portStartingPoint);
    }

    // TODO Need to use attributes for these so they are persisted (once a location is an entity),
    // rather than this deprecated approach of custom fields.
    @Override
    public RebindSupport<LocationMemento> getRebindSupport() {
        return new BasicLocationRebindSupport(this) {
            @Override public LocationMemento getMemento() {
                return getMementoWithProperties(MutableMap.<String,Object>of(
                        "mappings", mappings, 
                        "portReserved", portReserved.get(), 
                        "publicIpIdToHostname", publicIpIdToHostname));
            }
            @Override
            protected void doReconstruct(RebindContext rebindContext, LocationMemento memento) {
                super.doReconstruct(rebindContext, memento);
                mappings.putAll((Map<String, PortMapping>) memento.getCustomField("mappings"));
                portReserved.set((Integer)memento.getCustomField("portReserved"));
                publicIpIdToHostname.putAll((Map<String, String>)memento.getCustomField("publicIpIdToHostname"));
            }
        };
    }
    
    @Override
    public int acquirePublicPort(String publicIpId) {
        int port;
        synchronized (this) {
            // far too simple -- see javadoc above
            port = getNextPort();
            
            // TODO When delete deprecated code, stop registering PortMapping until associate() is called
            PortMapping mapping = new PortMapping(publicIpId, port, null, -1);
            log.debug(this+" allocating public port "+port+" on "+publicIpId+" (no association info yet)");
            
            mappings.put(makeKey(publicIpId, port), mapping);
        }
        onChanged();
        return port;
    }

    protected int getNextPort() {
        // far too simple -- see javadoc above
        return portReserved.incrementAndGet();
    }
    
    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort) {
        associateImpl(publicIpId, publicEndpoint, l, privatePort);
    }

    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, int privatePort) {
        associateImpl(publicIpId, publicEndpoint, null, privatePort);
    }

    protected void associateImpl(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort) {
        synchronized (this) {
            String publicIp = publicEndpoint.getHostText();
            int publicPort = publicEndpoint.getPort();
            recordPublicIpHostname(publicIpId, publicIp);
            PortMapping mapping = new PortMapping(publicIpId, publicEndpoint, l, privatePort);
            PortMapping oldMapping = getPortMappingWithPublicSide(publicIpId, publicPort);
            log.debug(this+" associating public "+publicEndpoint+" on "+publicIpId+" with private port "+privatePort+" at "+l+" ("+mapping+")"
                    +(oldMapping == null ? "" : " (overwriting "+oldMapping+" )"));
            mappings.put(makeKey(publicIpId, publicPort), mapping);
        }
        onChanged();
    }

    @Override
    public synchronized HostAndPort lookup(Location l, int privatePort) {
        for (PortMapping m: mappings.values()) {
            if (l.equals(m.target) && privatePort == m.privatePort)
                return getPublicHostAndPort(m);
        }
        return null;
    }
    
    @Override
    public synchronized HostAndPort lookup(String publicIpId, int privatePort) {
        for (PortMapping m: mappings.values()) {
            if (publicIpId.equals(m.publicIpId) && privatePort==m.privatePort)
                return getPublicHostAndPort(m);
        }
        return null;
    }
    
    @Override
    public boolean forgetPortMapping(String publicIpId, int publicPort) {
        PortMapping old;
        synchronized (this) {
            old = mappings.remove(makeKey(publicIpId, publicPort));
            log.debug("cleared port mapping for "+publicIpId+":"+publicPort+" - "+old);
        }
        if (old != null) onChanged();
        return (old != null);
    }
    
    @Override
    public boolean forgetPortMappings(Location l) {
        List<PortMapping> result = Lists.newArrayList();
        synchronized (this) {
            for (Iterator<PortMapping> iter = result.iterator(); iter.hasNext();) {
                PortMapping m = iter.next();
                if (l.equals(m.target)) {
                    iter.remove();
                    result.add(m);
                }
            }
        }
        log.debug("cleared all port mappings for "+l+" - "+result);
        if (!result.isEmpty()) {
            onChanged();
        }
        return !result.isEmpty();
    }
    
    @Override
    protected ToStringHelper string() {
        return super.string().add("scope", getScope()).add("mappingsSize", mappings.size());
    }

    @Override
    public String toVerboseString() {
        return string().add("mappings", mappings).toString();
    }

    @Override
    public String getScope() {
        return checkNotNull(getConfig(SCOPE), "scope");
    }

    @Override
    public boolean isClient() {
        return false;
    }

    protected String makeKey(String publicIpId, int publicPort) {
        return publicIpId+":"+publicPort;
    }

    
    ///////////////////////////////////////////////////////////////////////////////////
    // Internal state, for generating memento
    ///////////////////////////////////////////////////////////////////////////////////

    public List<PortMapping> getPortMappings() {
        synchronized (this) {
            return ImmutableList.copyOf(mappings.values());
        }
    }
    
    public Map<String, Integer> getPortCounters() {
        return ImmutableMap.of("global", portReserved.get());
    }

    
    ///////////////////////////////////////////////////////////////////////////////////
    // Deprecated
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    @Deprecated
    public PortMapping acquirePublicPortExplicit(String publicIpId, int port) {
        PortMapping mapping = new PortMapping(publicIpId, port, null, -1);
        log.debug("assigning explicit public port "+port+" at "+publicIpId);
        PortMapping result = mappings.put(makeKey(publicIpId, port), mapping);
        onChanged();
        return result;
    }

    @Override
    @Deprecated
    public boolean forgetPortMapping(PortMapping m) {
        return forgetPortMapping(m.publicIpId, m.publicPort);
    }

    @Override
    @Deprecated
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress) {
        log.debug("recording public IP "+publicIpId+" associated with "+hostnameOrPublicIpAddress);
        synchronized (publicIpIdToHostname) {
            String old = publicIpIdToHostname.put(publicIpId, hostnameOrPublicIpAddress);
            if (old!=null && !old.equals(hostnameOrPublicIpAddress))
                log.warn("Changing hostname recorded against public IP "+publicIpId+"; from "+old+" to "+hostnameOrPublicIpAddress);
        }
        onChanged();
    }

    @Override
    @Deprecated
    public String getPublicIpHostname(String publicIpId) {
        synchronized (publicIpIdToHostname) {
            return publicIpIdToHostname.get(publicIpId);
        }
    }
    
    @Override
    @Deprecated
    public boolean forgetPublicIpHostname(String publicIpId) {
        log.debug("forgetting public IP "+publicIpId+" association");
        boolean result;
        synchronized (publicIpIdToHostname) {
            result = (publicIpIdToHostname.remove(publicIpId) != null);
        }
        onChanged();
        return result;
    }

    @Override
    @Deprecated
    public int acquirePublicPort(String publicIpId, Location l, int privatePort) {
        int publicPort;
        synchronized (this) {
            PortMapping old = getPortMappingWithPrivateSide(l, privatePort);
            // only works for 1 public IP ID per location (which is the norm)
            if (old!=null && old.publicIpId.equals(publicIpId)) {
                log.debug("request to acquire public port at "+publicIpId+" for "+l+":"+privatePort+", reusing old assignment "+old);
                return old.getPublicPort();
            }
            
            publicPort = acquirePublicPort(publicIpId);
            log.debug("request to acquire public port at "+publicIpId+" for "+l+":"+privatePort+", allocating "+publicPort);
            associateImpl(publicIpId, publicPort, l, privatePort);
        }
        onChanged();
        return publicPort;
    }

    @Override
    @Deprecated
    public void associate(String publicIpId, int publicPort, Location l, int privatePort) {
        synchronized (this) {
            associateImpl(publicIpId, publicPort, l, privatePort);
        }
        onChanged();
    }

    protected void associateImpl(String publicIpId, int publicPort, Location l, int privatePort) {
        synchronized (this) {
            PortMapping mapping = new PortMapping(publicIpId, publicPort, l, privatePort);
            PortMapping oldMapping = getPortMappingWithPublicSide(publicIpId, publicPort);
            log.debug("associating public port "+publicPort+" on "+publicIpId+" with private port "+privatePort+" at "+l+" ("+mapping+")"
                    +(oldMapping == null ? "" : " (overwriting "+oldMapping+" )"));
            mappings.put(makeKey(publicIpId, publicPort), mapping);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // Internal only; make protected when deprecated interface method removed
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public HostAndPort getPublicHostAndPort(PortMapping m) {
        if (m.publicEndpoint == null) {
            String hostname = getPublicIpHostname(m.publicIpId);
            if (hostname==null)
                throw new IllegalStateException("No public hostname associated with "+m.publicIpId+" (mapping "+m+")");
            return HostAndPort.fromParts(hostname, m.publicPort);
        } else {
            return m.publicEndpoint;
        }
    }

    @Override
    public synchronized PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort) {
        return mappings.get(makeKey(publicIpId, publicPort));
    }

    @Override
    public synchronized Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        for (PortMapping m: mappings.values())
            if (publicIpId.equals(m.publicIpId)) result.add(m);
        return result;
    }

    /** returns the subset of port mappings associated with a given location */
    @Override
    public synchronized Collection<PortMapping> getLocationPublicIpIds(Location l) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        for (PortMapping m: mappings.values())
            if (l.equals(m.getTarget())) result.add(m);
        return result;
    }

    @Override
    public synchronized PortMapping getPortMappingWithPrivateSide(Location l, int privatePort) {
        for (PortMapping m: mappings.values())
            if (l.equals(m.getTarget()) && privatePort==m.privatePort) return m;
        return null;
    }
}
