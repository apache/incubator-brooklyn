package brooklyn.entity.group

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.event.basic.DependentConfiguration
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.management.Task
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends SoftwareProcessEntity {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    @SetFromFlag("portNumberSensor")  //TODO javadoc; and make default be HTTP_PORT
    public static final BasicConfigKey<Sensor> PORT_NUMBER_SENSOR = [ Sensor, "member.sensor.portNumber", "Port number sensor on members" ]

    @SetFromFlag("port")  //TODO get standard name; ideally inherit the standard field
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = [ String, "proxy.protocol", "Protocol", "http" ]
    @SetFromFlag("domain")
    public static final BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]
    @SetFromFlag("url")
    public static final BasicAttributeSensorAndConfigKey<String> URL = [ String, "proxy.url", "URL" ]

    @SetFromFlag
    Cluster cluster
    String domain
    int port
    String protocol
    String url
    Sensor portNumber

    AbstractMembershipTrackingPolicy policy
    protected Set<String> addresses = new LinkedHashSet<String>()
    

    public AbstractController(Map properties=[:], Entity owner=null, Cluster cluster=null) {
        super(properties, owner)

        // TODO Are these checks too early? What if someone subsequently calls setConfig;
        // why must they have already set the URL etc?

        // use http port by default
        portNumber = getConfig(PORT_NUMBER_SENSOR)
        if (portNumber==null) portNumber = HTTP_PORT;

        // FIXME shouldn't have these as vars and config keys; just use a getter method
        // TODO needs to be discovered/obtained
        port = getConfig(HTTP_PORT)?.iterator()?.next() ?: 8000
        protocol = getConfig(PROTOCOL)
        domain = getConfig(DOMAIN_NAME)

        if (getConfig(URL)) {
	        url = getConfig(URL.configKey)
	        setAttribute(URL, url)

            // Set attributes from URL
            URI uri = new URI(url)
            if (port==null) port = uri.port; else assert port==uri.port : "mismatch between port and uri $url for $this"
            if (protocol==null) protocol = uri.scheme; else assert protocol==uri.scheme : "mismatch between port and uri $url for $this"
            if (domain==null) domain = uri.host; else assert domain==uri.host : "mismatch between domain and uri $url for $this"
        } else {
            // Set attributes from properties or config with defaults
            url = "${protocol}://${domain}:${port}/";
	        setAttribute(URL, url)
        }
        setAttribute(HTTP_PORT, port)
        setAttribute(PROTOCOL, protocol)
        setAttribute(DOMAIN_NAME, domain)
        
        Preconditions.checkNotNull(domain, "Domain must be set for controller")

        policy = new AbstractMembershipTrackingPolicy() {
            //FIXME seems to be getting invoked twice
            protected void onEntityAdded(Entity member) { addEntity(member); }
            protected void onEntityRemoved(Entity member) { removeEntity(member); }
        }
    }

    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'cluster'.
     */
    public void bind(Map flags) {
        this.cluster = flags.cluster ?: this.cluster
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getAttribute(HTTP_PORT)) result.add(getAttribute(HTTP_PORT))
        return result
    }

    //FIXME members locations might be remote?
    public void addEntity(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("About to add to $displayName, new member ${member.displayName} in locations ${member.locations} - waiting for service to be up")

        //FIXME messy way to prevent subscriptions from applying until service is up
        //anyway, this is the wrong place for that logic; should be in update        
        Task started = DependentConfiguration.attributeWhenReady(member, SoftwareProcessEntity.SERVICE_UP)
        executionContext.submit(started)
        started.get()
        
        LOG.info("Adding to $displayName, new member ${member.displayName} in locations ${member.locations}")
        Set oldAddresses = new LinkedHashSet(addresses)
        member.locations.each { MachineLocation machine ->
            String ip = machine.address.hostAddress
            int port = member.getAttribute(portNumber)
            addresses.add("${ip}:${port}")
        }
        if (addresses==oldAddresses)
            //FIXME no change; shouldn't happen but it does
            return;
        update()
    }
    
    public void removeEntity(Entity member) {
        LOG.info("Removing from $displayName, member ${member.displayName} previously in locations ${member.locations}")
        
        Set oldAddresses = new LinkedHashSet(addresses)
        member.locations.each { MachineLocation machine ->
            String ip = machine.address.hostAddress
            int port = member.getAttribute(portNumber)
            addresses.remove("${ip}:${port}")
        }
        if (addresses==oldAddresses)
            //FIXME no change; shouldn't happen but it does
            return;
        update()
    }
    
    public void start(Collection<? extends Location> locations) {
        addPolicy(policy)
        reset()
        super.start(locations)
    }

    public void update() {
        if (getAttribute(SoftwareProcessEntity.SERVICE_UP)) {
            configure()
            restart()
        }
    }

    public void reset() {
        policy.reset()
        addresses.clear()
        policy.setGroup(cluster)
    }

	
	protected void preStop() {
		super.preStop()
        policy.reset()
        addresses.clear()
    }
}
