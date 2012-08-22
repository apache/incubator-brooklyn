package brooklyn.entity.proxy;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.PreStart;
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.entity.group.Cluster
import brooklyn.entity.trait.Startable
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends SoftwareProcessEntity {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class);

    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicConfigKey<Sensor> PORT_NUMBER_SENSOR = new BasicConfigKey<Sensor>(
            Sensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port)", Attributes.HTTP_PORT);

    //TODO make independent from web; push web-logic to subclass (AbstractWebController) with default 8000
    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "Main HTTP port where this proxy listens", [8000,"8001+"]);
    
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
    
    @SetFromFlag
    Cluster cluster;
    
    String domain;
    Integer port;
    String protocol;
    String url;
    Sensor portNumber;

    AbstractMembershipTrackingPolicy policy;
    protected Set<String> addresses = new LinkedHashSet<String>();
    protected Set<Entity> targets = new LinkedHashSet<Entity>();
    

    public AbstractController(Map properties=[:], Entity owner=null, Cluster cluster=null) {
        super(properties, owner);

        policy = new AbstractMembershipTrackingPolicy(name: "Controller targets tracker") {
            protected void onEntityChange(Entity member) { checkEntity(member); }
            protected void onEntityAdded(Entity member) { addEntity(member); }
            protected void onEntityRemoved(Entity member) { removeEntity(member); }
        }
    }

    @Description("Forces reload of the configuration")
    public abstract void reload();
    
    protected void makeUrl() {
        if (url==null || url.contains("://"+ANONYMOUS+":")) {
            String hostname = domain;
            // use 'hostname' instead of domain if domain is anonymous
            if (hostname==null || hostname==ANONYMOUS) hostname = getAttribute(HOSTNAME);
            if (hostname==null) hostname = ANONYMOUS;
            if (protocol==null) {
                if (url!=null && !url.startsWith("null:")) protocol = url.substring(0, url.indexOf(':'));
                else protocol = getConfig(SSL_CONFIG)!=null ? "https" : "http";
            }
            url = protocol+"://"+hostname+":"+port+"/";
            setAttribute(SPECIFIED_URL, url)
        }
    }
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'cluster'.
     */
    public void bind(Map flags) {
        this.cluster = flags.cluster ?: this.cluster;
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (getAttribute(PROXY_HTTP_PORT)) result.add(getAttribute(PROXY_HTTP_PORT));
        return result;
    }

    protected void preStart() {
        super.preStart();
        
        // use http port by default
        portNumber = getConfig(PORT_NUMBER_SENSOR);

        port = getAttribute(PROXY_HTTP_PORT);
        Preconditions.checkNotNull(port, "Port must be set for controller");
        
        protocol = getConfig(PROTOCOL);
        domain = getConfig(DOMAIN_NAME);

        if (!getConfig(SPECIFIED_URL)) {
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
            LOG.debug("Members of {}, checking {}, eliminating because not up", displayName, member.displayName);
            return false;
        }
        if (!cluster.members.contains(member)) {
            LOG.debug("Members of {}, checking {}, eliminating because not member", displayName, member.displayName);
            return false;
        }
        LOG.debug("Members of {}, checking {}, approving", displayName, member.displayName);
        return true;
    }
    
    //FIXME members locations might be remote?
    public synchronized void addEntity(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("Considering to add to {}, new member {} in locations {} - "+
                "waiting for service to be up", displayName, member.displayName, member.locations);
        if (targets.contains(member)) return;
        
        if (!member.getAttribute(Startable.SERVICE_UP)) {
            LOG.debug("Members of {}, not adding {} because not yet up", displayName, member.displayName);
            return;
        }
        
        Set oldAddresses = new LinkedHashSet(addresses);
        for (MachineLocation machine : member.locations) {
            //use hostname as this is more portable (eg in amazon, ip doesn't resolve)
            String ip = machine.address.hostName;
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
        LOG.info("Adding to {}, new member {} in locations {}", displayName, member.displayName, member.locations);
        
        // TODO shouldn't need to do this here? (no harm though)
        makeUrl();
        
        update();
        targets.add(member);
    }
    
    public synchronized void removeEntity(Entity member) {
        if (!targets.contains(member)) return;
        
        Set oldAddresses = new LinkedHashSet(addresses);
        for (MachineLocation machine : member.locations) {
            String ip = machine.address.hostAddress;
            int port = member.getAttribute(portNumber);
            addresses.remove(ip+":"+port);
        }
        if (addresses==oldAddresses) {
            LOG.debug("when removing from {}, member {}, not found (already removed?)", displayName, member.displayName);
            return;
        }
        
        LOG.info("Removing from {}, member {} previously in locations {}", displayName, member.displayName, member.locations);
        update();
        targets.remove(member);
    }
    
    boolean isActive = false;
    boolean updateNeeded = true;
    
    public void start(Collection<? extends Location> locations) {
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
            LOG.info("updating {}", this);
            reconfigureService();
            LOG.debug("submitting restart for update to {}", this);
            invoke(RELOAD);
        }
        setAttribute(TARGETS, addresses);
    }

    public void reset() {
        policy.reset();
        addresses.clear();
        if (cluster)
            policy.setGroup(cluster);
        setAttribute(TARGETS, addresses);
    }

	
	protected void preStop() {
		super.preStop();
        policy.reset();
        addresses.clear();
        setAttribute(TARGETS, addresses);
    }
}
