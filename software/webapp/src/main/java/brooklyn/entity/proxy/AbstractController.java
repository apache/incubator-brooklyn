package brooklyn.entity.proxy;

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
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends SoftwareProcessEntity implements LoadBalancer {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class);

    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicConfigKey<AttributeSensor> PORT_NUMBER_SENSOR = new BasicConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port)", Attributes.HTTP_PORT);

    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "Main HTTP port where this proxy listens", ImmutableList.of(8000,"8001+"));
    
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.protocol", "Main URL protocol this proxy answers (typically http or https)", null);
    
    //does this have special meaning to nginx/others? or should we just take the hostname ?
    public static final String ANONYMOUS = "anonymous";
    
    @SetFromFlag("domain")
    public static final BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name that this controller responds to", ANONYMOUS);
        
    @SetFromFlag("url")
    public static final BasicAttributeSensorAndConfigKey<String> SPECIFIED_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.url", "Main URL this proxy listens at");
    
    @SetFromFlag("ssl")
    public static final BasicConfigKey<ProxySslConfig> SSL_CONFIG = 
        new BasicConfigKey<ProxySslConfig>(ProxySslConfig.class, "proxy.ssl.config", "configuration (e.g. certificates) for SSL; will use SSL if set, not use SSL if not set");

    public static final BasicAttributeSensor<Set> TARGETS = new BasicAttributeSensor<Set>(
            Set.class, "proxy.targets", "Main set of downstream targets");
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector(AbstractController.class, "reload");
    
    protected String domain;
    protected Integer port;
    protected String protocol;
    protected String url;
    protected AttributeSensor<Integer> portNumber;
    protected boolean isActive = false;
    protected boolean updateNeeded = true;
    protected Group serverPool;

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

    public String getDomain() {
        return domain;
    }
    
    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getUrl() {
        return url;
    }

    @Description("Forces reload of the configuration")
    public abstract void reload();
    
    protected void makeUrl() {
        if (url==null || url.contains("://"+ANONYMOUS+":")) {
            String hostname = domain;
            // use 'hostname' instead of domain if domain is anonymous
            if (hostname==null || hostname==ANONYMOUS) {
                hostname = getAttribute(HOSTNAME);
                if (hostname!=null) {
                    domain = hostname;
                    setConfigEvenIfOwned(DOMAIN_NAME, hostname);
                    setAttribute(DOMAIN_NAME, hostname);
                } else {
                    LOG.warn("Unable to determine domain/hostname for "+this);
                }
            }
            if (hostname==null) hostname = ANONYMOUS;
            if (protocol==null) {
                if (url!=null && !url.startsWith("null:")) protocol = url.substring(0, url.indexOf(':'));
                else protocol = getConfig(SSL_CONFIG)!=null ? "https" : "http";
            }
            url = protocol+"://"+hostname+":"+port+"/";
            setAttribute(SPECIFIED_URL, url);
        }
    }
    
    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(PROXY_HTTP_PORT))) result.add(getAttribute(PROXY_HTTP_PORT));
        return result;
    }

    protected void preStart() {
        super.preStart();
        
        // use http port by default
        portNumber = checkNotNull(getConfig(PORT_NUMBER_SENSOR));

        port = getAttribute(PROXY_HTTP_PORT);
        Preconditions.checkNotNull(port, "Port must be set for controller");
        
        protocol = getConfig(PROTOCOL);
        domain = getConfig(DOMAIN_NAME);

        if (!groovyTruth(getConfig(SPECIFIED_URL))) {
            // previously we would attempt to infer values from a specified URL, but now we don't
            // as that specified URL might be for another machine with port-forwarding
        } else {
            makeUrl();
        }
        setAttribute(PROTOCOL, protocol);
        setAttribute(DOMAIN_NAME, domain);
        
        Preconditions.checkNotNull(domain, "Domain must be set for controller");
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
            Integer port = member.getAttribute(portNumber);
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
        
        // TODO shouldn't need to do this here? (no harm though)
        makeUrl();
        
        update();
        targets.add(member);
    }
    
    public synchronized void removeEntity(Entity member) {
        if (!targets.contains(member)) return;
        
        Set oldAddresses = new LinkedHashSet(addresses);
        for (Location loc : member.getLocations()) {
            MachineLocation machine = (MachineLocation) loc;
            String ip = machine.getAddress().getHostAddress();
            int port = member.getAttribute(portNumber);
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
        if (!isActive) updateNeeded = true;
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
