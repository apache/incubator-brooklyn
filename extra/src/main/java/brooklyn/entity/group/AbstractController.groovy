package brooklyn.entity.group

import groovy.util.ObservableList.ChangeType
import groovy.util.ObservableList.ElementEvent

import java.beans.PropertyChangeListener
import java.nio.charset.Charset;
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractService {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    public static final BasicConfigKey<Integer> SUGGESTED_HTTP_PORT = [ Integer, "proxy.httpPort", "Suggested proxy HTTP port" ]
    public static final BasicConfigKey<String> DOMAIN_NAME = [ String, "proxy.domainName", "Domain name" ]

    public static final BasicAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT

    Cluster cluster
    String domain
    Map<InetAddress,List<Integer>> addresses = [:].withDefault []
    BasicAttributeSensor<Integer> portNumber

    public AbstractController(Map properties=[:], Group owner=null, Cluster cluster) {
        super(properties, owner)

        portNumber = properties.portNumber ?: 80
        setAttribute(HTTP_PORT, portNumber)

        domain = getConfig(DOMAIN_NAME) ?: properties.domain
        Preconditions.checkNotNull(domain, "Domain must be set for controller")
        setConfig(DOMAIN_NAME, domain)

        setCluster(cluster ?: properties.cluster)
    }

    public void setCluster(Cluster cluster) {
        Preconditions.checkNotNull cluster, "The cluster cannot be null"
        this.cluster = cluster
        cluster.setOwner(this)
        cluster.addEntityChangeListener({ ElementEvent event ->
                LOG.trace "Entity change event for {} - {}", id, event.changeType
	            switch (event.changeType) {
                    case ChangeType.ADDED:
                        add [ event.newValue ]
                        break
                    case ChangeType.MULTI_ADD:
                        add event.values
                        break
                    case ChangeType.REMOVED:
	                    remove [ event.oldValue ]
                        break;
                    case ChangeType.MULTI_REMOVE:
                        remove event.values
                        break
                    case ChangeType.CLEARED:
                        remove event.values
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

    public void configure() {
        File file = new File("/tmp/${id}")
        Files.write(getConfigFile(), file, Charsets.UTF_8)
        locations.each { SshMachineLocation machine -> machine.copyTo file, "${machine.setup.runDir}/conf/server.conf" }
        restart()
    }
    
    public abstract String getConfigFile()
}
