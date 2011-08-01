package brooklyn.entity.group

import java.util.Collection
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.MachineLocation

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
    protected Map<InetAddress,List<Integer>> addresses
    

    public AbstractController(Map properties=[:], Entity owner=null, Cluster cluster=null) {
        super(properties, owner)

        portNumber = properties.portNumberSensor ?: getConfig(PORT_NUMBER_SENSOR)
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
        addPolicy(policy)
        reset()
    }

    /**
     * Expect to be passed the flags below, or to already have them from constructor:
     *  - cluster
     *  - domain
     *  - port
     *  - portNumberSensor
     */
    public void bind(Map flags) {
       throw new UnsupportedOperationException("TODO Move init of cluster etc to here?")
    }
   
    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getAttribute(HTTP_PORT)) result.add(getAttribute(HTTP_PORT))
        return result
    }

    //FIXME members locations might be remote?
    public void addEntity(Entity member) {
        member.locations.each { MachineLocation machine ->
            addresses[machine.address] += member.getAttribute(portNumber)
        }
        update();
    }
    
    public void removeEntity(Entity member) {
        member.locations.each { MachineLocation machine ->
            addresses[machine.address] -= member.getAttribute(portNumber)
        }
        update();
    }
    
    public void update() {
        if (getAttribute(AbstractService.SERVICE_UP)) {
            configure()
            restart()
        }
    }

    public void reset() {
        policy.reset()
        addresses = new HashMap<InetAddress,List<Integer>>().withDefault { new ArrayList<Integer>() }
        policy.setGroup(cluster)
    }

    @Override
    public void stop() {
        reset()
        cluster.stop()
        super.stop()
    }
}
