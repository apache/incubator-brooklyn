package brooklyn.entity.group

import groovy.util.ObservableList.ChangeType
import groovy.util.ObservableList.ElementEvent

import java.beans.PropertyChangeListener
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.group.Cluster
import brooklyn.entity.trait.Startable

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class Controller extends AbstractEntity implements Startable {
    public Cluster cluster

    public Controller(Map properties=[:], Group owner=null, Cluster cluster=null) {
        super(properties, owner)

        if (cluster) this.cluster = cluster

        cluster.addEntityChangeListener({ ElementEvent event ->
	            switch (event.changeType) {
                    case ChangeType.ADDED:
                        add event.newValue
                        break
                    case ChangeType.REMOVED:
	                    remove event.oldValue
                        break;
                    default:
                        break;
                }
            } as PropertyChangeListener)
    }

    public abstract void add(Entity entity);

    public abstract void remove(Entity entity);
}