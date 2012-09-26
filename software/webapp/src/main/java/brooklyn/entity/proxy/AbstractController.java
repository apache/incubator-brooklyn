package brooklyn.entity.proxy;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends SoftwareProcessEntity implements LoadBalancer {
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
            String.class, "proxy.domainName", "Domain name that this controller responds to", null);
        
    @SetFromFlag("ssl")
    public static final BasicConfigKey<ProxySslConfig> SSL_CONFIG = 
        new BasicConfigKey<ProxySslConfig>(ProxySslConfig.class, "proxy.ssl.config", "configuration (e.g. certificates) for SSL; will use SSL if set, not use SSL if not set");

    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;
    
    public static final BasicAttributeSensor<Set> TARGETS = new BasicAttributeSensor<Set>(
            Set.class, "proxy.targets", "Main set of downstream targets");
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector(AbstractController.class, "reload");
    
    protected Group serverPool;
    protected boolean isActive;
    protected boolean updateNeeded = true;

    AbstractMembershipTrackingPolicy policy;
    protected Set<String> addresses = new LinkedHashSet<String>();
    protected Set<Entity> targets = new LinkedHashSet<Entity>();
    
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

        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Controller targets tracker")) {
            protected void onEntityChange(Entity member) { checkEntity(member); }
            protected void onEntityAdded(Entity member) { addEntity(member); }
            protected void onEntityRemoved(Entity member) { removeEntity(member); }
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
        if (flags.containsKey("cluster")) {
            setConfig(SERVER_POOL, (Group) flags.get("cluster"));
        }
    }

    public boolean isActive() {
    	return isActive;
    }
    
    public String getProtocol() {
        return getAttribute(PROTOCOL);
    }

    public String getDomain() {
        return getAttribute(DOMAIN_NAME);
    }
    
    public Integer getPort() {
        return getAttribute(PROXY_HTTP_PORT);
    }

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
    
    protected String inferUrl() {
        String protocol = checkNotNull(getProtocol(), "protocol must not be null");
        String domain = checkNotNull(getDomain(), "domain must not be null");
        Integer port = checkNotNull(getPort(), "port must not be null");
        return protocol+"://"+domain+":"+port+"/";
    }
    
    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(PROXY_HTTP_PORT))) result.add(getAttribute(PROXY_HTTP_PORT));
        return result;
    }

    protected void preStart() {
        super.preStart();
        
        setAttribute(PROTOCOL, inferProtocol());
        setAttribute(DOMAIN_NAME, elvis(getConfig(DOMAIN_NAME), getAttribute(HOSTNAME)));
        setAttribute(ROOT_URL, inferUrl());
        
        checkNotNull(getPortNumberSensor(), "port number sensor must not be null");
    }
    
    public void checkEntity(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("Start {} checkEntity {}", this, member);
        if (belongs(member)) addEntity(member);
        else removeEntity(member);
        if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
    }
    
    public boolean belongs(Entity member) {
        if (!member.getAttribute(Startable.SERVICE_UP)) {
            LOG.debug("Members of {}, checking {}, eliminating because not up", getDisplayName(), member.getDisplayName());
            return false;
        }
        if (!serverPool.getMembers().contains(member)) {
            LOG.debug("Members of {}, checking {}, eliminating because not member", getDisplayName(), member.getDisplayName());
            return false;
        }
        LOG.debug("Members of {}, checking {}, approving", getDisplayName(), member.getDisplayName());
        return true;
    }
    
    //FIXME members locations might be remote?
    public synchronized void addEntity(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("Considering to add to {}, new member {} in locations {} - "+
                "waiting for service to be up", new Object[] {getDisplayName(), member.getDisplayName(), member.getLocations()});
        if (targets.contains(member)) return;
        
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            LOG.debug("Members of {}, not adding {} because not yet up", getDisplayName(), member.getDisplayName());
            return;
        }
        
        Set oldAddresses = new LinkedHashSet(addresses);
        for (Location loc : member.getLocations()) {
            MachineLocation machine = (MachineLocation) loc;
            //use hostname as this is more portable (eg in amazon, ip doesn't resolve)
            String ip = machine.getAddress().getHostName();
            Integer port = member.getAttribute(getPortNumberSensor());
            if (ip==null || port==null) {
                LOG.warn("Missing ip/port for web controller {} target {}, skipping", this, member);
            } else {
                addresses.add(ip+":"+port);
            }
        }
        if (addresses==oldAddresses) {
            if (LOG.isTraceEnabled()) LOG.trace("invocation of {}.addEntity({}) causes no change", this, member);
            return;
        }
        LOG.info("Adding to {}, new member {} in locations {}", new Object[] {getDisplayName(), member.getDisplayName(), member.getLocations()});
        
        update();
        targets.add(member);
    }
    
    public synchronized void removeEntity(Entity member) {
        if (!targets.contains(member)) return;
        
        Set oldAddresses = new LinkedHashSet(addresses);
        for (Location loc : member.getLocations()) {
            MachineLocation machine = (MachineLocation) loc;
            String ip = machine.getAddress().getHostAddress();
            int port = member.getAttribute(getPortNumberSensor());
            addresses.remove(ip+":"+port);
        }
        if (addresses==oldAddresses) {
            LOG.debug("when removing from {}, member {}, not found (already removed?)", getDisplayName(), member.getDisplayName());
            return;
        }
        
        LOG.info("Removing from {}, member {} previously in locations {}", 
                new Object[] {getDisplayName(), member.getDisplayName(), member.getLocations()});
        update();
        targets.remove(member);
    }
    
    public void start(Collection<? extends Location> locations) {
        // TODO Should not add policy before NginxController is properly started; otherwise
        // get callbacks for addEntity when fields like portNumber are still null.
        serverPool = getConfig(SERVER_POOL);
        LOG.info("Adding policy {} to {} on AbstractController.start", policy, this);
        addPolicy(policy);
        reset();
        super.start(locations);
        isActive = true;
        update();
    }

    /** should set up so that 'addresses' are targeted */
    protected abstract void reconfigureService();
    
    public void update() {
        if (!isActive()) updateNeeded = true;
        else {
            updateNeeded = false;
            LOG.debug("Updating {} in response to changes", this);
            reconfigureService();
            LOG.debug("Submitting restart for update to {}", this);
            invokeFromJava(RELOAD);
        }
        setAttribute(TARGETS, addresses);
    }

    public void reset() {
        policy.reset();
        addresses.clear();
        if (groovyTruth(serverPool)) {
            policy.setGroup(serverPool);
        }
        setAttribute(TARGETS, addresses);
    }

	
	protected void preStop() {
		super.preStop();
        policy.reset();
        addresses.clear();
        setAttribute(TARGETS, addresses);
    }
}
