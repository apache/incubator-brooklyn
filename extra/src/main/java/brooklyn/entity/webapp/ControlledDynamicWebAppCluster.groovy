package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.group.AbstractController
import brooklyn.entity.trait.Startable
import brooklyn.location.Location

import com.google.common.base.Preconditions

/**
 * This group contains all the sub-groups and entities that go in to a single location.
 *
 * These are:
 * <ul>
 * <li>a {@link DynamicCluster} of {@link JavaWebApp}s
 * <li>a cluster controller
 * <li>a {@link Policy} to resize the DynamicCluster
 * </ul>
 */
public class ControlledDynamicWebAppCluster extends AbstractEntity implements Startable {

    DynamicWebAppCluster cluster
    AbstractController controller
    Closure webServerFactory
    
    ControlledDynamicWebAppCluster(Map flags, Entity owner = null) {
        super(flags, owner)
        
        controller = Preconditions.checkNotNull flags.get('controller'), "'controller' property is mandatory"
        webServerFactory = Preconditions.checkNotNull flags.get('webServerFactory'), "'webServerFactory' property is mandatory"
        
        addOwnedChild(controller)
        cluster = new DynamicWebAppCluster(newEntity:webServerFactory, this)
        
        setAttribute(SERVICE_UP, false)
    }

    void start(Collection<Location> locations) {
        cluster.start(locations)

        controller.bind(cluster:cluster)
        controller.start(locations)

        setAttribute(SERVICE_UP, true)
    }

    void stop() {
        controller.stop()
        cluster.stop()

        setAttribute(SERVICE_UP, false)
    }

    void restart() {
        throw new UnsupportedOperationException()
    }
}
