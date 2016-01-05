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
package org.apache.brooklyn.entity.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractControllerImpl extends SoftwareProcessImpl implements AbstractController {
    
    // TODO Should review synchronization model. Currently, all changes to the serverPoolTargets
    // (and checking for potential changes) is done while synchronized on serverPoolAddresses. That means it 
    // will also call update/reload while holding the lock. This is "conservative", but means
    // sub-classes need to be extremely careful about any additional synchronization and of
    // their implementations of update/reconfigureService/reload.
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractControllerImpl.class);

    protected volatile boolean isActive;
    protected volatile boolean updateNeeded = true;

    protected AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy;
    // final because this is the synch target
    final protected Set<String> serverPoolAddresses = Sets.newLinkedHashSet();
    protected Map<Entity,String> serverPoolTargets = Maps.newLinkedHashMap();
    
    public AbstractControllerImpl() {
        this(MutableMap.of(), null, null);
    }
    public AbstractControllerImpl(Map<?, ?> properties) {
        this(properties, null, null);
    }
    public AbstractControllerImpl(Entity parent) {
        this(MutableMap.of(), parent, null);
    }
    public AbstractControllerImpl(Map<?, ?> properties, Entity parent) {
        this(properties, parent, null);
    }
    public AbstractControllerImpl(Entity parent, Cluster cluster) {
        this(MutableMap.of(), parent, cluster);
    }
    public AbstractControllerImpl(Map<?, ?> properties, Entity parent, Cluster cluster) {
        super(properties, parent);
    }

    @Override
    public void init() {
        super.init();
        sensors().set(SERVER_POOL_TARGETS, ImmutableMap.<Entity, String>of());
    }
    
    protected void addServerPoolMemberTrackingPolicy() {
        Group serverPool = getServerPool();
        if (serverPool == null) {
            return; // no-op
        }
        if (serverPoolMemberTrackerPolicy != null) {
            LOG.debug("Call to addServerPoolMemberTrackingPolicy when serverPoolMemberTrackingPolicy already exists, removing and re-adding, in {}", this);
            removeServerPoolMemberTrackingPolicy();
        }
        for (Policy p: policies()) {
            if (p instanceof ServerPoolMemberTrackerPolicy) {
                // TODO want a more elegant idiom for this!
                LOG.info(this+" picking up "+p+" as the tracker (already set, often due to rebind)");
                serverPoolMemberTrackerPolicy = (ServerPoolMemberTrackerPolicy) p;
                return;
            }
        }
        
        AttributeSensor<?> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        AttributeSensor<?> hostnameSensor = getConfig(HOSTNAME_SENSOR);
        AttributeSensor<?> portSensor = getConfig(PORT_NUMBER_SENSOR);
        Set<AttributeSensor<?>> sensorsToTrack;
        if (hostAndPortSensor != null) {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostAndPortSensor);
        } else {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostnameSensor, portSensor);
        }
        
        serverPoolMemberTrackerPolicy = policies().add(PolicySpec.create(ServerPoolMemberTrackerPolicy.class)
                .displayName("Controller targets tracker")
                .configure("group", serverPool)
                .configure("sensorsToTrack", sensorsToTrack));

        LOG.info("Added policy {} to {}", serverPoolMemberTrackerPolicy, this);
        
        // Initialize ourselves immediately with the latest set of members; don't wait for
        // listener notifications because then will be out-of-date for short period (causing 
        // problems for rebind)
        Map<Entity,String> serverPoolTargets = Maps.newLinkedHashMap();
        for (Entity member : getServerPool().getMembers()) {
            if (belongsInServerPool(member)) {
                if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
                String address = getAddressOfEntity(member);
                serverPoolTargets.put(member, address);
            }
        }

        LOG.info("Resetting {}, server pool targets {}", new Object[] {this, serverPoolTargets});
        sensors().set(SERVER_POOL_TARGETS, serverPoolTargets);
    }
    
    protected void removeServerPoolMemberTrackingPolicy() {
        if (serverPoolMemberTrackerPolicy != null) {
            policies().remove(serverPoolMemberTrackerPolicy);
        }
    }
    
    public static class ServerPoolMemberTrackerPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity entity) {
            // relies on policy-rebind injecting the implementation rather than the dynamic-proxy
            ((AbstractControllerImpl)super.entity).onServerPoolMemberChanged(entity);
        }
    }
    
    @Override
    public Set<String> getServerPoolAddresses() {
        return ImmutableSet.copyOf(Iterables.filter(getAttribute(SERVER_POOL_TARGETS).values(), Predicates.notNull()));
    }

    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    @Override
    public void bind(Map<?,?> flags) {
        if (flags.containsKey("serverPool")) {
            setConfigEvenIfOwned(SERVER_POOL, (Group) flags.get("serverPool"));
        } 
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onManagementNoLongerMaster() {
        super.onManagementNoLongerMaster(); // TODO remove when deprecated method in parent removed
        isActive = false;
        removeServerPoolMemberTrackingPolicy();
    }

    private Group getServerPool() {
        return getConfig(SERVER_POOL);
    }
    
    @Override
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public boolean isSsl() {
        return getSslConfig() != null;
    }
    
    @Override
    public ProxySslConfig getSslConfig() {
        return getConfig(SSL_CONFIG);
    }
    
    @Override
    public String getProtocol() {
        return getAttribute(PROTOCOL);
    }

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    @Override
    public String getDomain() {
        return getAttribute(DOMAIN_NAME);
    }
    
    @Override
    public Integer getPort() {
        if (isSsl())
            return getAttribute(PROXY_HTTPS_PORT);
        else
            return getAttribute(PROXY_HTTP_PORT);
    }

    /** primary URL this controller serves, if one can / has been inferred */
    @Override
    public String getUrl() {
        return Strings.toString( getAttribute(MAIN_URI) );
    }

    @Override
    public AttributeSensor<Integer> getPortNumberSensor() {
        return getAttribute(PORT_NUMBER_SENSOR);
    }

    @Override
    public AttributeSensor<String> getHostnameSensor() {
        return getAttribute(HOSTNAME_SENSOR);
    }

    @Override
    public AttributeSensor<String> getHostAndPortSensor() {
        return getAttribute(HOST_AND_PORT_SENSOR);
    }
    
    @Override
    public abstract void reload();

    protected String inferProtocol() {
        return isSsl() ? "https" : "http";
    }
    
    /** returns URL, if it can be inferred; null otherwise */
    protected String inferUrl(boolean requireManagementAccessible) {
        String protocol = checkNotNull(getProtocol(), "no protocol configured");
        String domain = getDomain();
        if (domain != null && domain.startsWith("*.")) {
            domain = domain.replace("*.", ""); // Strip wildcard
        }
        Integer port = checkNotNull(getPort(), "no port configured (the requested port may be in use)");
        if (requireManagementAccessible) {
            HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, port);
            if (accessible!=null) {
                domain = accessible.getHostText();
                port = accessible.getPort();
            }
        }
        if (domain==null) domain = Machines.findSubnetHostname(this).orNull();
        if (domain==null) return null;
        return protocol+"://"+domain+":"+port+"/"+getConfig(SERVICE_UP_URL_PATH);
    }

    protected String inferUrl() {
        return inferUrl(false);
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(PROXY_HTTP_PORT))) result.add(getAttribute(PROXY_HTTP_PORT));
        if (groovyTruth(getAttribute(PROXY_HTTPS_PORT))) result.add(getAttribute(PROXY_HTTPS_PORT));
        return result;
    }

    @Override
    protected void preStart() {
        super.preStart();
        computePortsAndUrls();
    }
    
    protected void computePortsAndUrls() {
        AttributeSensor<String> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        Maybe<Object> hostnameSensor = config().getRaw(HOSTNAME_SENSOR);
        Maybe<Object> portSensor = config().getRaw(PORT_NUMBER_SENSOR);
        if (hostAndPortSensor != null) {
            checkState(!hostnameSensor.isPresent() && !portSensor.isPresent(), 
                    "Must not set %s and either of %s or %s", HOST_AND_PORT_SENSOR, HOSTNAME_SENSOR, PORT_NUMBER_SENSOR);
        }

        ConfigToAttributes.apply(this);

        sensors().set(PROTOCOL, inferProtocol());
        sensors().set(MAIN_URI, URI.create(inferUrl()));
        sensors().set(ROOT_URL, inferUrl());
 
        checkNotNull(getPortNumberSensor(), "no sensor configured to infer port number");
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        // TODO when rebind policies, and rebind calls connectSensors, then this will cause problems.
        // Also relying on addServerPoolMemberTrackingPolicy to set the serverPoolAddresses and serverPoolTargets.

        addServerPoolMemberTrackingPolicy();
    }
    
    @Override
    protected void postStart() {
        super.postStart();
        isActive = true;
        update();
    }

    @Override
    protected void postRebind() {
        super.postRebind();
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (state != null && state == Lifecycle.RUNNING) {
            isActive = true;
            updateNeeded();
        }
    }

    @Override
    protected void preStop() {
        super.preStop();
        removeServerPoolMemberTrackingPolicy();
    }

    /** 
     * Implementations should update the configuration so that 'serverPoolAddresses' are targeted.
     * The caller will subsequently call reload to apply the new configuration.
     */
    protected abstract void reconfigureService();
    
    public void updateNeeded() {
        synchronized (serverPoolAddresses) {
            if (updateNeeded) return;
            updateNeeded = true;
            LOG.debug("queueing an update-needed task for "+this+"; update will occur shortly");
            Entities.submit(this, Tasks.builder().displayName("update-needed").body(new Runnable() {
                @Override
                public void run() {
                    if (updateNeeded)
                        AbstractControllerImpl.this.update();
                } 
            }).build());
        }
    }
    
    @Override
    public void update() {
        try {
            Task<?> task = updateAsync();
            if (task != null) task.getUnchecked();
            ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(this, "update");
        } catch (Exception e) {
            ServiceStateLogic.ServiceProblemsLogic.updateProblemsIndicator(this, "update", "update failed with: "+Exceptions.collapseText(e));
            throw Exceptions.propagate(e);
        }
    }
    
    public Task<?> updateAsync() {
        synchronized (serverPoolAddresses) {
            Task<?> result = null;
            if (!isActive()) updateNeeded = true;
            else {
                updateNeeded = false;
                LOG.debug("Updating {} in response to changes", this);
                LOG.info("Updating {}, server pool targets {}", new Object[] {this, getAttribute(SERVER_POOL_TARGETS)});
                reconfigureService();
                LOG.debug("Reloading {} in response to changes", this);
                // reload should happen synchronously
                result = invoke(RELOAD);
            }
            return result;
        }
    }

    protected void onServerPoolMemberChanged(Entity member) {
        synchronized (serverPoolAddresses) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, considering membership of {} which is in locations {}", 
                new Object[] {this, member, member.getLocations()});
            if (belongsInServerPool(member)) {
                addServerPoolMember(member);
            } else {
                removeServerPoolMember(member);
            }
            if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
        }
    }
    
    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getServerPool().getMembers().contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, approving", this, member);
        return true;
    }
    
    protected void addServerPoolMember(Entity member) {
        synchronized (serverPoolAddresses) {
            String oldAddress = getAttribute(SERVER_POOL_TARGETS).get(member);
            String newAddress = getAddressOfEntity(member);
            if (Objects.equal(newAddress, oldAddress)) {
                if (LOG.isTraceEnabled())
                    if (LOG.isTraceEnabled()) LOG.trace("Ignoring unchanged address {}", oldAddress);
                return;
            } else if (newAddress == null) {
                LOG.info("Removing from {}, member {} with old address {}, because inferred address is now null", new Object[] {this, member, oldAddress});
            } else {
                if (oldAddress != null) {
                    LOG.info("Replacing in {}, member {} with old address {}, new address {}", new Object[] {this, member, oldAddress, newAddress});
                } else {
                    LOG.info("Adding to {}, new member {} with address {}", new Object[] {this, member, newAddress});
                }
            }

            if (Objects.equal(oldAddress, newAddress)) {
                if (LOG.isTraceEnabled()) LOG.trace("For {}, ignoring change in member {} because address still {}", new Object[] {this, member, newAddress});
                return;
            }

            // TODO this does it synchronously; an async method leaning on `updateNeeded` and `update` might
            // be more appropriate, especially when this is used in a listener
            MapAttribute.put(this, SERVER_POOL_TARGETS, member, newAddress);
            updateAsync();
        }
    }
    
    protected void removeServerPoolMember(Entity member) {
        synchronized (serverPoolAddresses) {
            if (!getAttribute(SERVER_POOL_TARGETS).containsKey(member)) {
                if (LOG.isTraceEnabled()) LOG.trace("For {}, not removing as don't have member {}", new Object[] {this, member});
                return;
            }

            String address = MapAttribute.remove(this, SERVER_POOL_TARGETS, member);

            LOG.info("Removing from {}, member {} with address {}", new Object[] {this, member, address});

            updateAsync();
        }
    }
    
    protected String getAddressOfEntity(Entity member) {
        AttributeSensor<String> hostAndPortSensor = getHostAndPortSensor();
        if (hostAndPortSensor != null) {
            String result = member.getAttribute(hostAndPortSensor);
            if (result != null) {
                return result;
            } else {
                LOG.error("No host:port set for {} (using attribute {}); skipping in {}", 
                        new Object[] {member, hostAndPortSensor, this});
                return null;
            }
        } else {
            String ip = member.getAttribute(getHostnameSensor());
            Integer port = member.getAttribute(getPortNumberSensor());
            if (ip!=null && port!=null) {
                return ip+":"+port;
            }
            LOG.error("Unable to construct hostname:port representation for {} ({}:{}); skipping in {}", 
                    new Object[] {member, ip, port, this});
            return null;
        }
    }

    // Utilities for modifying an AttributeSensor of type map
    private static class MapAttribute {
        public static <K, V> V put(Entity entity, AttributeSensor<Map<K,V>> attribute, K key, V value) {
            Map<K, V> oldMap = entity.getAttribute(attribute);
            Map<K, V> newMap = MutableMap.copyOf(oldMap);
            V oldVal = newMap.put(key, value);
            ((EntityInternal)entity).sensors().set(attribute, newMap);
            return oldVal;
        }
        
        public static <K, V> V remove(Entity entity, AttributeSensor<Map<K,V>> attribute, K key) {
            Map<K, V> oldMap = entity.getAttribute(attribute);
            Map<K, V> newMap = MutableMap.copyOf(oldMap);
            V oldVal = newMap.remove(key);
            ((EntityInternal)entity).sensors().set(attribute, newMap);
            return oldVal;
        }
    }
}
