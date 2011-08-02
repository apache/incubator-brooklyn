package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.policy.Policy

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

    ControlledDynamicWebAppCluster cluster
    NginxController controller
    Closure webServerFactory
    
    ControlledDynamicWebAppCluster(Map props, Entity owner = null) {
        super(props, owner)
        
        controller = Preconditions.checkArgument properties.remove('controller'), "'controller' property is mandatory"
        webServerFactory = Preconditions.checkArgument properties.remove('webServerFactory'), "'webServerFactory' property is mandatory"
        Preconditions.checkArgument controller instanceof Entity, "'controller' must be an Entity"
        Preconditions.checkArgument webServerFactory instanceof Closure, "'webServerFactory' must be a closure"
        
        addOwnedChild(controller)
        cluster = new ControlledDynamicWebAppCluster(newEntity:webServerFactory, this)
        
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
