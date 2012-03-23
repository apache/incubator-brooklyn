package brooklyn.entity.webapp

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.group.AbstractController
import brooklyn.entity.group.Cluster;
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions

/**
 * This group contains all the sub-groups and entities that go in to a single location.
 *
 * These are:
 * <ul>
 * <li>a {@link brooklyn.entity.group.DynamicCluster} of {@link JavaWebAppService}s
 * <li>a cluster controller
 * <li>a {@link brooklyn.policy.Policy} to resize the DynamicCluster
 * </ul>
 */
public class ControlledDynamicWebAppCluster extends AbstractEntity implements Startable {
    DynamicWebAppCluster cluster

    @SetFromFlag(immutable=true)
    AbstractController controller
    
    @SetFromFlag(immutable=true)
    Closure webServerFactory

    @SetFromFlag('initialSize')
    public static BasicConfigKey<Integer> INITIAL_SIZE = [ Cluster.INITIAL_SIZE, 1 ]
        
    ControlledDynamicWebAppCluster(Map flags, Entity owner = null) {
        super(flags, owner)
        setAttribute(SERVICE_UP, false)
    }
    
    Entity configure(Map flags=[:]) {
        boolean alreadyHadController = (controller!=null);
        
        super.configure(flags)
        
        if (controller && !alreadyHadController) 
            addOwnedChild(controller)
        if (webServerFactory && !cluster)
            cluster = new DynamicWebAppCluster(this,
                newEntity:webServerFactory, initialSize:getConfig(INITIAL_SIZE))
            
        return this
	}

    void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull controller, "'controller' property is mandatory"
        Preconditions.checkNotNull webServerFactory, "'webServerFactory' property is mandatory"
        
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
