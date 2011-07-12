package brooklyn.entity.group

import groovy.util.ObservableList.ChangeType
import groovy.util.ObservableList.ElementEvent

import java.beans.PropertyChangeListener
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineLocation

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractService {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    public static final BasicConfigKey<Integer> SUGGESTED_HTTP_PORT = [ Integer, "proxy.httpPort", "Suggested proxy HTTP port" ]
    public static final BasicConfigKey<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]
    public static final BasicConfigKey<String> PROTOCOL = [ String, "proxy.portNumber", "Protocol" ]
    public static final BasicConfigKey<URL> URL = [ String, "proxy.url", "URL" ]

    public static final BasicAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT

    AbstractGroup cluster
    String domain
    int port
    String protocol
    URL url
    Map<InetAddress,List<Integer>> addresses

    public AbstractController(Map properties=[:], Entity owner=null, Group cluster=null) {
        super(properties, owner)

        if (getConfig(PROTOCOL) || properties.url in URL) {
	        url = getConfig(URL) ?: properties.url
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

	        setConfig(URL, new URL("${protocol}://${domain}:${port}"))
        }

        setCluster(cluster ?: properties.cluster)

        addresses = new HashMap<InetAddress,List<Integer>>().withDefault { new ArrayList<Integer>() }
    }

    public void setCluster(Group cluster) {
        Preconditions.checkNotNull cluster, "The cluster cannot be null"
        this.cluster = cluster
        cluster.setOwner(this)
        cluster.addEntityChangeListener({ ElementEvent event ->
                LOG.trace "Entity change event for {} - {}", id, event.changeType
	            switch (event.changeType) {
                    case ChangeType.ADDED:
                        add([ event.newValue ])
                        break
                    case ChangeType.MULTI_ADD:
                        add(event.values)
                        break
                    case ChangeType.REMOVED:
	                    remove([ event.oldValue ])
                        break;
                    case ChangeType.MULTI_REMOVE:
                        remove(event.values)
                        break
                    case ChangeType.CLEARED:
                        remove(event.values)
                        break
                    default:
                        break;
                }
            } as PropertyChangeListener)
    }

    public void add(Collection<Entity> entities) {
        entities.each { Entity e -> e.locations.each { MachineLocation machine -> addresses[machine.address] += e.getAttribute(portNumber) } }
        configure()
    }

    public void remove(Collection<Entity> entities) {
        entities.each { Entity e -> e.locations.each { MachineLocation machine -> addresses[machine.address] -= e.getAttribute(portNumber) } }
        configure()
    }

    /**
     * Configure the controller based on the cluster membership list.
     */
    public abstract void configure()
}
