package brooklyn.entity.group;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.SoftwareProcessEntity
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
            Sensor.class, "member.sensor.portNumber", "Port number sensor on members");

    //TODO make independent from web; push web-logic to subclass (AbstractWebController) with default 8000
    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "HTTP port", [8000,"8001+"]);
    
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.protocol", "Protocol", "http");
    
    //does this have special meaning to nginx/others? or should we just take the hostname ?
    public static final String ANONYMOUS = "anonymous";
    
    @SetFromFlag("domain")
    public static final BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name", ANONYMOUS);
        
    @SetFromFlag("url")
    public static final BasicAttributeSensorAndConfigKey<String> SPECIFIED_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.url", "URL this proxy controller responds to");
    
    public static final BasicAttributeSensor<Set> TARGETS = new BasicAttributeSensor<Set>(
            Set.class, "proxy.targets", "Downstream targets");
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector(AbstractController.class, "reload");
    
    @SetFromFlag
    Cluster cluster;
    
    String domain;
    int port;
    String protocol;
    String url;
    Sensor portNumber;

    AbstractMembershipTrackingPolicy policy;
    protected Set<String> addresses = new LinkedHashSet<String>();
    protected Set<Entity> targets = new LinkedHashSet<Entity>();
    

    public AbstractController(Map properties=[:], Entity owner=null, Cluster cluster=null) {
        super(properties, owner);

        // TODO Are these checks too early? What if someone subsequently calls setConfig;
        // why must they have already set the URL etc?

        // use http port by default
        portNumber = getConfig(PORT_NUMBER_SENSOR);
        if (portNumber==null) portNumber = Attributes.HTTP_PORT;

        // FIXME shouldn't have these as vars and config keys; just use a getter method
        // TODO needs to be discovered/obtained
        port = getConfig(PROXY_HTTP_PORT)?.iterator()?.next() ?: 8000;
        protocol = getConfig(PROTOCOL);
        domain = getConfig(DOMAIN_NAME);

        if (getConfig(SPECIFIED_URL)) {
	        url = getConfig(SPECIFIED_URL);
	        setAttribute(SPECIFIED_URL, url);

            // Set attributes from URL
            URI uri = new URI(url)
            if (port==null) port = uri.port; else assert port==uri.port : "mismatch between port and uri "+url+" for "+this;
            if (protocol==null) protocol = uri.scheme; else assert protocol==uri.scheme : "mismatch between port and uri "+url+" for "+this;
            if (domain==null) domain = uri.host; else assert domain==uri.host : "mismatch between domain and uri "+url+" for "+this;
        } else {
            // Set attributes from properties or config with defaults
            makeUrl();
        }
        setAttribute(PROXY_HTTP_PORT, port);
        setAttribute(PROTOCOL, protocol);
        setAttribute(DOMAIN_NAME, domain);
        
        Preconditions.checkNotNull(domain, "Domain must be set for controller");

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
