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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.mementos.LocationMemento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.BasicLocationRebindSupport;
import brooklyn.entity.rebind.RebindContext;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

/**
 * 
 * @author aled
 *
 * TODO This implementation is not efficient, and currently has a cap of about 50000 rules.
 * Need to improve the efficiency and scale.
 * A quick win could be to use a different portReserved counter for each publicIpId,
 * when calling acquirePublicPort?
 * 
 * TODO Callers need to be more careful in acquirePublicPort for which ports are actually in use.
 * If multiple apps sharing the same public-ip (e.g. in the same vcloud-director vOrg) then they 
 * must not allocate the same public port (e.g. ensure they share the same PortForwardManager
 * by using the same scope in 
 * {@code managementContext.getLocationRegistry().resolve("portForwardManager(scope=global)")}.
 * However, this still doesn't check if the port is *actually* available. For example, if a
 * different Brooklyn instance is also deploying there then we can get port conflicts, or if 
 * some ports in that range are already in use (e.g. due to earlier dev/test runs) then this
 * will not be respected. Callers should probably figure out the port number themselves, but
 * that also leads to concurrency issues.
 * 
 * TODO The publicIpId means different things to different callers:
 * <ul>
 *   <li> In acquirePublicPort() it is (often?) an identifier of the actual public ip.
 *   <li> In later calls to associate(), it is (often?) an identifier for the target machine
 *        such as the jcloudsMachine.getJcloudsId().
 * </ul>
 */
@SuppressWarnings("serial")
public class PortForwardManagerImpl extends AbstractLocation implements PortForwardManager {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerImpl.class);
    
    protected final Map<String,PortMapping> mappings = new LinkedHashMap<String,PortMapping>();

    private final Map<AssociationListener, Predicate<? super AssociationMetadata>> associationListeners = new ConcurrentHashMap<AssociationListener, Predicate<? super AssociationMetadata>>();

    @Deprecated
    protected final Map<String,String> publicIpIdToHostname = new LinkedHashMap<String,String>();
    
    // horrible hack -- see javadoc above
    private final AtomicInteger portReserved = new AtomicInteger(11000);

    private final Object mutex = new Object();
    
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
                Map<String, PortMapping> mappingsCopy;
                Map<String,String> publicIpIdToHostnameCopy;
                synchronized (mutex) {
                    mappingsCopy = MutableMap.copyOf(mappings);
                    publicIpIdToHostnameCopy = MutableMap.copyOf(publicIpIdToHostname);
                }
                return getMementoWithProperties(MutableMap.<String,Object>of(
                        "mappings", mappingsCopy, 
                        "portReserved", portReserved.get(), 
                        "publicIpIdToHostname", publicIpIdToHostnameCopy));
            }
            @Override
            protected void doReconstruct(RebindContext rebindContext, LocationMemento memento) {
                super.doReconstruct(rebindContext, memento);
                mappings.putAll( Preconditions.checkNotNull((Map<String, PortMapping>) memento.getCustomField("mappings"), "mappings was not serialized correctly"));
                portReserved.set( (Integer)memento.getCustomField("portReserved"));
                publicIpIdToHostname.putAll( Preconditions.checkNotNull((Map<String, String>)memento.getCustomField("publicIpIdToHostname"), "publicIpIdToHostname was not serialized correctly") );
            }
        };
    }
    
    @Override
    public int acquirePublicPort(String publicIpId) {
        int port;
        synchronized (mutex) {
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
        return portReserved.getAndIncrement();
    }
    
    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort) {
        associateImpl(publicIpId, publicEndpoint, l, privatePort);
        emitAssociationCreatedEvent(publicIpId, publicEndpoint, l, privatePort);
    }

    @Override
    public void associate(String publicIpId, HostAndPort publicEndpoint, int privatePort) {
        associateImpl(publicIpId, publicEndpoint, null, privatePort);
        emitAssociationCreatedEvent(publicIpId, publicEndpoint, null, privatePort);
    }

    protected void associateImpl(String publicIpId, HostAndPort publicEndpoint, Location l, int privatePort) {
        synchronized (mutex) {
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

    private void emitAssociationCreatedEvent(String publicIpId, HostAndPort publicEndpoint, Location location, int privatePort) {
        AssociationMetadata metadata = new AssociationMetadata(publicIpId, publicEndpoint, location, privatePort);
        for (Map.Entry<AssociationListener, Predicate<? super AssociationMetadata>> entry : associationListeners.entrySet()) {
            if (entry.getValue().apply(metadata)) {
                try {
                    entry.getKey().onAssociationCreated(metadata);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    log.warn("Exception thrown when emitting association creation event " + metadata, e);
                }
            }
        }
    }

    @Override
    public HostAndPort lookup(Location l, int privatePort) {
        synchronized (mutex) {
            for (PortMapping m: mappings.values()) {
                if (l.equals(m.target) && privatePort == m.privatePort)
                    return getPublicHostAndPort(m);
            }
        }
        return null;
    }
    
    @Override
    public HostAndPort lookup(String publicIpId, int privatePort) {
        synchronized (mutex) {
            for (PortMapping m: mappings.values()) {
                if (publicIpId.equals(m.publicIpId) && privatePort==m.privatePort)
                    return getPublicHostAndPort(m);
            }
        }
        return null;
    }
    
    @Override
    public boolean forgetPortMapping(String publicIpId, int publicPort) {
        PortMapping old;
        synchronized (mutex) {
            old = mappings.remove(makeKey(publicIpId, publicPort));
            if (old != null) {
                emitAssociationDeletedEvent(associationMetadataFromPortMapping(old));
            }
            log.debug("cleared port mapping for "+publicIpId+":"+publicPort+" - "+old);
        }
        if (old != null) onChanged();
        return (old != null);
    }
    
    @Override
    public boolean forgetPortMappings(Location l) {
        List<PortMapping> result = Lists.newArrayList();
        synchronized (mutex) {
            for (Iterator<PortMapping> iter = mappings.values().iterator(); iter.hasNext();) {
                PortMapping m = iter.next();
                if (l.equals(m.target)) {
                    iter.remove();
                    result.add(m);
                    emitAssociationDeletedEvent(associationMetadataFromPortMapping(m));
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("cleared all port mappings for "+l+" - "+result);
        if (!result.isEmpty()) {
            onChanged();
        }
        return !result.isEmpty();
    }
    
    @Override
    public boolean forgetPortMappings(String publicIpId) {
        List<PortMapping> result = Lists.newArrayList();
        synchronized (mutex) {
            for (Iterator<PortMapping> iter = mappings.values().iterator(); iter.hasNext();) {
                PortMapping m = iter.next();
                if (publicIpId.equals(m.publicIpId)) {
                    iter.remove();
                    result.add(m);
                    emitAssociationDeletedEvent(associationMetadataFromPortMapping(m));
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("cleared all port mappings for "+publicIpId+" - "+result);
        if (!result.isEmpty()) {
            onChanged();
        }
        return !result.isEmpty();
    }

    private void emitAssociationDeletedEvent(AssociationMetadata metadata) {
        for (Map.Entry<AssociationListener, Predicate<? super AssociationMetadata>> entry : associationListeners.entrySet()) {
            if (entry.getValue().apply(metadata)) {
                try {
                    entry.getKey().onAssociationDeleted(metadata);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    log.warn("Exception thrown when emitting association creation event " + metadata, e);
                }
            }
        }
    }
    
    @Override
    protected ToStringHelper string() {
        int size;
        synchronized (mutex) {
            size = mappings.size();
        }
        return super.string().add("scope", getScope()).add("mappingsSize", size);
    }

    @Override
    public String toVerboseString() {
        String mappingsStr;
        synchronized (mutex) {
            mappingsStr = mappings.toString();
        }
        return string().add("mappings", mappingsStr).toString();
    }

    @Override
    public String getScope() {
        return checkNotNull(getConfig(SCOPE), "scope");
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public void addAssociationListener(AssociationListener listener, Predicate<? super AssociationMetadata> filter) {
        associationListeners.put(listener, filter);
    }

    @Override
    public void removeAssociationListener(AssociationListener listener) {
        associationListeners.remove(listener);
    }

    protected String makeKey(String publicIpId, int publicPort) {
        return publicIpId+":"+publicPort;
    }

    private AssociationMetadata associationMetadataFromPortMapping(PortMapping portMapping) {
        String publicIpId = portMapping.getPublicEndpoint().getHostText();
        HostAndPort publicEndpoint = portMapping.getPublicEndpoint();
        Location location = portMapping.getTarget();
        int privatePort = portMapping.getPrivatePort();
        return new AssociationMetadata(publicIpId, publicEndpoint, location, privatePort);
    }
    
    ///////////////////////////////////////////////////////////////////////////////////
    // Internal state, for generating memento
    ///////////////////////////////////////////////////////////////////////////////////

    public List<PortMapping> getPortMappings() {
        synchronized (mutex) {
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
        PortMapping result;
        synchronized (mutex) {
            result = mappings.put(makeKey(publicIpId, port), mapping);
        }
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
        synchronized (mutex) {
            String old = publicIpIdToHostname.put(publicIpId, hostnameOrPublicIpAddress);
            if (old!=null && !old.equals(hostnameOrPublicIpAddress))
                log.warn("Changing hostname recorded against public IP "+publicIpId+"; from "+old+" to "+hostnameOrPublicIpAddress);
        }
        onChanged();
    }

    @Override
    @Deprecated
    public String getPublicIpHostname(String publicIpId) {
        synchronized (mutex) {
            return publicIpIdToHostname.get(publicIpId);
        }
    }
    
    @Override
    @Deprecated
    public boolean forgetPublicIpHostname(String publicIpId) {
        log.debug("forgetting public IP "+publicIpId+" association");
        boolean result;
        synchronized (mutex) {
            result = (publicIpIdToHostname.remove(publicIpId) != null);
        }
        onChanged();
        return result;
    }

    @Override
    @Deprecated
    public int acquirePublicPort(String publicIpId, Location l, int privatePort) {
        int publicPort;
        synchronized (mutex) {
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
        synchronized (mutex) {
            associateImpl(publicIpId, publicPort, l, privatePort);
        }
        onChanged();
    }

    protected void associateImpl(String publicIpId, int publicPort, Location l, int privatePort) {
        synchronized (mutex) {
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
    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort) {
        synchronized (mutex) {
            return mappings.get(makeKey(publicIpId, publicPort));
        }
    }

    @Override
    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        synchronized (mutex) {
            for (PortMapping m: mappings.values())
                if (publicIpId.equals(m.publicIpId)) result.add(m);
        }
        return result;
    }

    /** returns the subset of port mappings associated with a given location */
    @Override
    public Collection<PortMapping> getLocationPublicIpIds(Location l) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        synchronized (mutex) {
            for (PortMapping m: mappings.values())
                if (l.equals(m.getTarget())) result.add(m);
        }
        return result;
    }

    @Override
    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort) {
        synchronized (mutex) {
            for (PortMapping m: mappings.values())
                if (l.equals(m.getTarget()) && privatePort==m.privatePort) return m;
        }
        return null;
    }
}
