package brooklyn.entity.proxy;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.rebind.BasicEntityRebindSupport;
import brooklyn.entity.rebind.RebindContext;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.mementos.EntityMemento;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends SoftwareProcessEntity implements LoadBalancer {
    
    // TODO Should review synchronization model. Currently, all changes to the serverPoolTargets
    // (and checking for potential changes) is done while synchronized on this. That means it 
    // will also call update/reload while holding the lock. This is "conservative", but means
    // sub-classes need to be extremely careful about any additional synchronization and of
    // their implementations of update/reconfigureService/reload.
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class);

    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> PORT_NUMBER_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port)", Attributes.HTTP_PORT);

    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "Main HTTP port where this proxy listens", ImmutableList.of(8000,"8001+"));
    
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.protocol", "Main URL protocol this proxy answers (typically http or https)", null);
    
    @SetFromFlag("domain")
    public static final BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name that this controller responds to, or null if it responds to all domains", null);
        
    @SetFromFlag("ssl")
    public static final BasicConfigKey<ProxySslConfig> SSL_CONFIG = 
        new BasicConfigKey<ProxySslConfig>(ProxySslConfig.class, "proxy.ssl.config", "configuration (e.g. certificates) for SSL; will use SSL if set, not use SSL if not set");

    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;
    
    public static final BasicAttributeSensor<Set<String>> SERVER_POOL_TARGETS = new BasicAttributeSensor(
            Set.class, "proxy.serverpool.targets", "The downstream targets in the server pool");
    
    /**
     * @deprecated Use SERVER_POOL_TARGETS
     */
    public static final BasicAttributeSensor<Set<String>> TARGETS = SERVER_POOL_TARGETS;
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector(AbstractController.class, "reload");
    
    protected volatile boolean isActive;
    protected volatile boolean updateNeeded = true;

    protected AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy;
    protected Set<String> serverPoolAddresses = Sets.newLinkedHashSet();
    protected Set<Entity> serverPoolTargets = Sets.newLinkedHashSet();
    
    public AbstractController() {
        this(MutableMap.of(), null, null);
    }
    public AbstractController(Map properties) {
        this(properties, null, null);
    }
    public AbstractController(Entity owner) {
        this(MutableMap.of(), owner, null);
    }
    public AbstractController(Map properties, Entity owner) {
        this(properties, owner, null);
    }
    public AbstractController(Entity owner, Cluster cluster) {
        this(MutableMap.of(), owner, cluster);
    }
    public AbstractController(Map properties, Entity owner, Cluster cluster) {
        super(properties, owner);

        serverPoolMemberTrackerPolicy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Controller targets tracker")) {
            protected void onEntityChange(Entity member) { onServerPoolMemberChanged(member); }
            protected void onEntityAdded(Entity member) { onServerPoolMemberChanged(member); }
            protected void onEntityRemoved(Entity member) { onServerPoolMemberChanged(member); }
        };
    }

    @Override
    public Entity configure(Map flags) {
        Entity result = super.configure(flags);
        
        // Support old "cluster" flag (deprecated)
        if (flags.containsKey("cluster")) {
            Group cluster = (Group) flags.get("cluster");
            LOG.warn("Deprecated use of AbstractController.cluster: entity {}; value {}", this, cluster);
            if (getConfig(SERVER_POOL) == null) {
                setConfig(SERVER_POOL, cluster);
            }
        }
        
        return result;
    }
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'cluster'.
     */
    public void bind(Map flags) {
        if (flags.containsKey("serverPool")) {
            setConfig(SERVER_POOL, (Group) flags.get("serverPool"));
            
        } else if (flags.containsKey("cluster")) {
            LOG.warn("Deprecated use of AbstractController.cluster: entity {}; value {}", this, flags.get("cluster"));
            setConfig(SERVER_POOL, (Group) flags.get("cluster"));
        }
    }

    private Group getServerPool() {
        return getConfig(SERVER_POOL);
    }
    
    public boolean isActive() {
    	return isActive;
    }
    
    public String getProtocol() {
        return getAttribute(PROTOCOL);
    }

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    public String getDomain() {
        return getAttribute(DOMAIN_NAME);
    }
    
    public Integer getPort() {
        return getAttribute(PROXY_HTTP_PORT);
    }

    /** primary URL this controller serves, if one can / has been inferred */
    public String getUrl() {
        return getAttribute(ROOT_URL);
    }

    public AttributeSensor getPortNumberSensor() {
        return getAttribute(PORT_NUMBER_SENSOR);
    }

    @Description("Forces reload of the configuration")
    public abstract void reload();

    protected String inferProtocol() {
        return getConfig(SSL_CONFIG)!=null ? "https" : "http";
    }
    
    /** returns URL, if it can be inferred; null otherwise */
    protected String inferUrl() {
        String protocol = checkNotNull(getProtocol(), "protocol must not be null");
        String domain = getDomain();
        if (domain==null) domain = getAttribute(HOSTNAME);
        if (domain==null) return null;
        Integer port = checkNotNull(getPort(), "port must not be null");
        return protocol+"://"+domain+":"+port+"/";
    }
    
    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(PROXY_HTTP_PORT))) result.add(getAttribute(PROXY_HTTP_PORT));
        return result;
    }

    @Override
    protected void preStart() {
        super.preStart();
        
        setAttribute(PROTOCOL, inferProtocol());
        setAttribute(DOMAIN_NAME);
        setAttribute(ROOT_URL, inferUrl());
        
        checkNotNull(getPortNumberSensor(), "port number sensor must not be null");
    }
    
    @Override
    protected void postStart() {
        super.postStart();
        LOG.info("Adding policy {} to {}, during start", serverPoolMemberTrackerPolicy, this);
        addPolicy(serverPoolMemberTrackerPolicy);
        if (getUrl()==null) setAttribute(ROOT_URL, inferUrl());
        reset();
        isActive = true;
        update();
    }
    
    protected void preStop() {
        super.preStop();
        serverPoolMemberTrackerPolicy.reset();
    }

    /** 
     * Implementations should update the configuration so that 'serverPoolAddresses' are targeted.
     * The caller will subsequently call reload to apply the new configuration.
     */
    protected abstract void reconfigureService();
    
    public synchronized void update() {
        if (!isActive()) updateNeeded = true;
        else {
            updateNeeded = false;
            LOG.debug("Updating {} in response to changes", this);
            reconfigureService();
            LOG.debug("Reloading {} in response to changes", this);
            invokeFromJava(RELOAD);
        }
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
    }

    protected synchronized void reset() {
        serverPoolMemberTrackerPolicy.reset();
        serverPoolAddresses.clear();
        serverPoolTargets.clear();
        if (groovyTruth(getServerPool())) {
            serverPoolMemberTrackerPolicy.setGroup(getServerPool());
            
            // Initialize ourselves immediately with the latest set of members; don't wait for
            // listener notifications because then will be out-of-date for short period (causing 
            // problems for rebind)
            for (Entity member : getServerPool().getMembers()) {
                if (belongsInServerPool(member)) {
                    if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
                    serverPoolTargets.add(member);
                    String address = getAddressOfEntity(member);
                    if (address != null) {
                        serverPoolAddresses.add(address);
                    }
                }
            }
            
            LOG.info("Resetting {}, members {} with address {}", new Object[] {this, serverPoolTargets, serverPoolAddresses});
        }
        
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
    }

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("For {}, considering membership of {} which is in locations {}", 
                new Object[] {this, member, member.getLocations()});
        if (belongsInServerPool(member)) {
            addServerPoolMember(member);
        } else {
            removeServerPoolMember(member);
        }
        if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
    }
    
    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not up", getDisplayName(), member.getDisplayName());
            return false;
        }
        if (!getServerPool().getMembers().contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not member", getDisplayName(), member.getDisplayName());
            return false;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, approving", getDisplayName(), member.getDisplayName());
        return true;
    }
    
    protected synchronized void addServerPoolMember(Entity member) {
        if (serverPoolTargets.contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, not adding as already have member {}", new Object[] {this, member});
            return;
        }
        
        String address = getAddressOfEntity(member);
        if (address != null) {
            serverPoolAddresses.add(address);
        }

        LOG.info("Adding to {}, new member {} with address {}", new Object[] {this, member, address});
        
        update();
        serverPoolTargets.add(member);
    }
    
    protected synchronized void removeServerPoolMember(Entity member) {
        if (!serverPoolTargets.contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, not removing as don't have member {}", new Object[] {this, member});
            return;
        }
        
        String address = getAddressOfEntity(member);
        if (address != null) {
            serverPoolAddresses.remove(address);
        }
        
        LOG.info("Removing from {}, member {} with address {}", new Object[] {this, member, address});
        
        update();
        serverPoolTargets.remove(member);
    }
    
    protected String getAddressOfEntity(Entity member) {
        String ip = member.getAttribute(Attributes.HOSTNAME);
        Integer port = member.getAttribute(Attributes.HTTP_PORT);
        if (ip!=null && port!=null) {
            return ip+":"+port;
        }
        LOG.error("Unable to construct hostname:port representation for {} ({}:{}); skipping in {}", 
                new Object[] {member, ip, port, this});
        return null;
    }
	
    @Override
    public RebindSupport<EntityMemento> getRebindSupport() {
        return new BasicEntityRebindSupport(this) {
            @Override public EntityMemento getMemento() {
                // Note: using MutableMap so accepts nulls
            	Map<String, Object> flags = Maps.newLinkedHashMap();
            	flags.put("serverPool", (serverPool != null ? serverPool.getId() : null));
            	flags.put("addresses", addresses);
            	flags.put("targets", Iterables.transform(targets, entityIdFunction));
            	flags.put("isActive", isActive);
                return super.getMementoWithProperties(flags);
            }
            @Override protected void doRebind(RebindContext rebindContext, EntityMemento memento) {
            	super.doRebind(rebindContext, memento);
            	String serverPoolId = (String) memento.getProperty("serverPool");
				serverPool = (Group) (serverPoolId != null ? rebindContext.getEntity(serverPoolId) : null);
				addresses.addAll((Set<String>) memento.getProperty("addresses"));
				for (String targetId : (Set<String>)memento.getProperty("targets")) {
					targets.add(rebindContext.getEntity(targetId));
				}
				isActive = (Boolean) memento.getProperty("isActive");
            }
        };
    }
    
    private final Function<Entity, String> entityIdFunction = new Function<Entity, String>() {
		@Override
		@Nullable
		public String apply(@Nullable Entity input) {
			return (input != null) ? input.getId() : null;
		}
    	
	};
}
