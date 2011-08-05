package brooklyn.entity.group

import java.util.Collection
import java.util.List
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.event.basic.DependentConfiguration
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.management.Task

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractService {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    public static final BasicConfigKey<Sensor> PORT_NUMBER_SENSOR = [ String, "member.sensor.portNumber", "Port number sensor on members" ]

    public static final ConfiguredAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT
    public static final ConfiguredAttributeSensor<String> PROTOCOL = [ String, "proxy.protocol", "Protocol", "http" ]
    public static final ConfiguredAttributeSensor<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]
    public static final ConfiguredAttributeSensor<String> URL = [ String, "proxy.url", "URL" ]

    Cluster cluster
    String domain
    int port
    String protocol
    String url
    Sensor portNumber

    AbstractMembershipTrackingPolicy policy
    protected List<String> addresses = new LinkedList<String>()
    

    public AbstractController(Map properties=[:], Entity owner=null, Cluster cluster=null) {
        super(properties, owner)

        setConfigIfValNonNull(PORT_NUMBER_SENSOR, properties.portNumberSensor)
        setConfigIfValNonNull(URL.configKey, properties.url)
        setConfigIfValNonNull(HTTP_PORT.configKey, properties.port)
        setConfigIfValNonNull(PROTOCOL.configKey, properties.protocol)
        setConfigIfValNonNull(DOMAIN_NAME.configKey, properties.domain)
        
        // TODO Are these checks too early? What if someone subsequently calls setConfig;
        // why must they have already set the URL etc?

        portNumber = getConfig(PORT_NUMBER_SENSOR)
        Preconditions.checkNotNull(portNumber, "The port number sensor must be supplied")

        if (getConfig(URL.configKey) || properties.containsKey("url")) {
	        url = properties.url ?: getConfig(URL.configKey)
	        setAttribute(URL, url)

            // Set attributes from URL
            URI uri = new URI(url)
            port = uri.port
            setAttribute(HTTP_PORT, port)
            protocol = uri.scheme
            setAttribute(PROTOCOL, protocol)
            domain = uri.host
            setAttribute(DOMAIN_NAME, domain)
        } else {
            // Set attributes from properties or config with defaults
	        port = properties.port ?: getConfig(HTTP_PORT.configKey)
	        setAttribute(HTTP_PORT, port)

	        protocol = properties.protocol ?: getConfig(PROTOCOL.configKey)
	        setAttribute(PROTOCOL, protocol)

            domain = properties.domain ?: getConfig(DOMAIN_NAME.configKey)
            Preconditions.checkNotNull(domain, "Domain must be set for controller")
            setAttribute(DOMAIN_NAME, domain)

	        setAttribute(URL, "${protocol}://${domain}:${port}/")
        }

        policy = new AbstractMembershipTrackingPolicy() {
            protected void onEntityAdded(Entity member) { addEntity(member); }
            protected void onEntityRemoved(Entity member) { removeEntity(member); }
        }

        this.cluster = cluster ?: properties.cluster
    }

    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'cluster'.
     */
    public void bind(Map flags) {
        this.cluster = flags.cluster ?: this.cluster
    }

    @Override
    public void start(Collection<Location> locations) {
        addPolicy(policy)
        reset()
        
        super.start(locations)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getAttribute(HTTP_PORT)) result.add(getAttribute(HTTP_PORT))
        return result
    }

    //FIXME members locations might be remote?
    public void addEntity(Entity member) {
        Task started = DependentConfiguration.attributeWhenReady(member, AbstractService.SERVICE_UP)
        executionContext.submit(started)
        started.get()
        member.locations.each { MachineLocation machine ->
            String ip = machine.address.hostAddress
            int port = member.getAttribute(portNumber)
            addresses.add("${ip}:${port}")
        }
        update()
    }
    
    public void removeEntity(Entity member) {
        member.locations.each { MachineLocation machine ->
            String ip = machine.address.hostAddress
            int port = member.getAttribute(portNumber)
            addresses.remove("${ip}:${port}")
        }
        update()
    }
    
    public void update() {
        if (getAttribute(AbstractService.SERVICE_UP)) {
            configure()
            restart()
        }
    }

    public void reset() {
        policy.reset()
        addresses.clear()
        policy.setGroup(cluster)
    }

    @Override
    public void stop() {
        reset()
        super.stop()
    }
}
