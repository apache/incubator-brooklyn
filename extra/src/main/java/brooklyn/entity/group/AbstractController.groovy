package brooklyn.entity.group

import groovy.util.ObservableList.ChangeType
import groovy.util.ObservableList.ElementEvent

import java.beans.PropertyChangeListener
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable

import com.google.common.base.Preconditions

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractController extends AbstractEntity implements Startable {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractController.class)

    Cluster cluster

    public AbstractController(Map properties=[:], Group owner=null, Cluster cluster) {
        super(properties, owner)

        setCluster(cluster ?: properties.cluster)
    }

    public void setCluster(Cluster cluster) {
        Preconditions.checkNotNull cluster, "The cluster cannot be null"
        this.cluster = cluster
        addOwnedChild(cluster)
        cluster.addEntityChangeListener({ ElementEvent event ->
                LOG.trace "Entity change event for {} - {}", id, event.changeType
	            switch (event.changeType) {
                    case ChangeType.ADDED:
                        add event.newValue
                        break
                    case ChangeType.MULTI_ADD:
                        event.values.each { add it }
                        break
                    case ChangeType.REMOVED:
	                    remove event.oldValue
                        break;
                    case ChangeType.MULTI_REMOVE:
                        event.values.each { remove it }
                        break
                    case ChangeType.CLEARED:
                        event.values.each { remove it }
                        break
                    default:
                        break;
                }
            } as PropertyChangeListener)
    }

    public abstract void add(Entity entity);

    public abstract void remove(Entity entity);
}