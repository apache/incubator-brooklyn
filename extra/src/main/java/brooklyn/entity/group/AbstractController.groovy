package brooklyn.entity.group

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineLocation
import brooklyn.management.SubscriptionHandle

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractService {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    public static final BasicConfigKey<Integer> SUGGESTED_HTTP_PORT = [ Integer, "proxy.httpPort", "Suggested proxy HTTP port" ]
    public static final BasicConfigKey<Sensor> PORT_NUMBER_SENSOR = [ String, "member.sensor.portNumber", "Port number sensor on members" ]

    public static final BasicAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT
    public static final BasicAttributeSensor<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]
    public static final BasicAttributeSensor<String> PROTOCOL = [ String, "proxy.portNumber", "Protocol" ]
    public static final BasicAttributeSensor<String> URL = [ String, "proxy.url", "URL" ]

    AbstractGroup cluster
    String domain
    int port
    String protocol
    String url
    Sensor portNumber

    AbstractMembershipTrackingPolicy policy
    protected Map<InetAddress,List<Integer>> addresses
    

    public AbstractController(Map properties=[:], Entity owner=null, AbstractGroup cluster=null) {
        super(properties, owner)

        portNumber = getConfig(PORT_NUMBER_SENSOR) ?: properties.portNumberSensor
        Preconditions.checkNotNull(portNumber, "The port number sensor must be supplied")

        if (getAttribute(PROTOCOL) || properties.containsKey("url")) {
	        url = getAttribute(URL) ?: properties.remove("url")
	        setAttribute(URL, url)

            // Set config properties from URL
            port = url.port
            setAttribute(HTTP_PORT, port)
            porotocol = url.protocol
            setAttribute(PROTOCOL, protocol)
            domain = url.host
            setAttribute(DOMAIN_NAME, domain)
        } else {
	        port = properties.port ?: 80
	        setAttribute(HTTP_PORT, port)

	        protocol = getAttribute(PROTOCOL) ?: properties.protocol ?: "http"
	        setAttribute(PROTOCOL, protocol)

            domain = getAttribute(DOMAIN_NAME) ?: properties.domain
            Preconditions.checkNotNull(domain, "Domain must be set for controller")
            setAttribute(DOMAIN_NAME, domain)

	        setAttribute(URL, "${protocol}://${domain}:${port}/")
        }

        policy = new AbstractMembershipTrackingPolicy() {
            protected void onEntityAdded(Entity member) { addEntity(member); }
            protected void onEntityRemoved(Entity member) { removeEntity(member); }
        }
        
        addPolicy(policy)
        reset()
        policy.setGroup(cluster ?: properties.cluster)
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
    }

    @Override
    public void stop() {
        reset()
        super.stop()
    }

    // TODO use blocking config mechanism to wait for the port number attribute to become available

    /**
     * Configure the controller based on the cluster membership list.
     */
    public abstract void configure()
}
