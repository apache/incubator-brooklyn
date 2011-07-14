package brooklyn.entity.group

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.EventListener;
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineLocation

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractService {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    public static final BasicConfigKey<Integer> SUGGESTED_HTTP_PORT = [ Integer, "proxy.httpPort", "Suggested proxy HTTP port" ]
    public static final BasicConfigKey<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]
    public static final BasicConfigKey<String> PROTOCOL = [ String, "proxy.portNumber", "Protocol" ]
    public static final BasicConfigKey<String> URL = [ String, "proxy.url", "URL" ]
    public static final BasicConfigKey<Sensor> PORT_NUMBER_SENSOR = [ String, "member.sensor.portNumber", "Port number sensor on members" ]
    
    public static final BasicAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT

    AbstractGroup cluster
    String domain
    int port
    String protocol
    URL url
    Sensor portNumber
    Map<InetAddress,List<Integer>> addresses

    public AbstractController(Map properties=[:], Entity owner=null, AbstractGroup cluster=null) {
        super(properties, owner)

        portNumber = getConfig(PORT_NUMBER_SENSOR) ?: properties.portNumberSensor
        Preconditions.checkNotNull(portNumber, "The port number sensor must be supplied")

        if (getConfig(PROTOCOL) || properties.containsKey("url")) {
	        url = getConfig(URL) ?: properties.remove("url")
	        setConfig(URL, url)

            // Set config properties from URL
            port = url.port
            setConfig(HTTP_PORT, port)
            porotocol = url.protocol
            setConfig(PROTOCOL, protocol)
            domain = url.host
            setConfig(DOMAIN_NAME, domain)
        } else {
	        port = properties.port ?: 80
	        setAttribute(HTTP_PORT, port)

	        protocol = getConfig(PROTOCOL) ?: properties.protocol ?: "http"
	        setConfig(PROTOCOL, protocol)

            domain = getConfig(DOMAIN_NAME) ?: properties.domain
            Preconditions.checkNotNull(domain, "Domain must be set for controller")
            setConfig(DOMAIN_NAME, domain)

	        setConfig(URL, "${protocol}://${domain}:${port}/")
        }

        setCluster(cluster ?: properties.cluster)

        addresses = new HashMap<InetAddress,List<Integer>>().withDefault { new ArrayList<Integer>() }
    }

    public void setCluster(AbstractGroup cluster) {
        Preconditions.checkNotNull cluster, "The cluster cannot be null"
        this.cluster = cluster
        subscriptionContext.subscribe(cluster, cluster.MEMBER_ADDED, { add it } as EventListener)
        subscriptionContext.subscribe(cluster, cluster.MEMBER_REMOVED, { remove it } as EventListener)
    }

    public synchronized void add(Entity entity) {
        entity.locations.each { MachineLocation machine -> addresses[machine.address] += entity.getAttribute(portNumber) }
        if (getAttribute(SERVICE_UP)) {
	        configure()
	        restart()
        }
    }

    public synchronized void remove(Entity entity) {
        entity.locations.each { MachineLocation machine -> addresses[machine.address] -= entity.getAttribute(portNumber) }
        if (getAttribute(SERVICE_UP)) {
	        configure()
	        restart()
        }
    }

    /**
     * Configure the controller based on the cluster membership list.
     */
    public abstract void configure()
}
