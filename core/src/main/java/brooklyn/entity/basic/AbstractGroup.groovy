package brooklyn.entity.basic

import java.util.Map
import java.util.concurrent.CopyOnWriteArraySet

import brooklyn.entity.Entity
import brooklyn.entity.Group

public abstract class AbstractGroup extends AbstractEntity implements Group {
    public AbstractGroup(Map props=[:]) {
        super(props)
    }

    final Collection<Entity> children = new CopyOnWriteArraySet<Entity>();

    /**
     * Adds argument as child of this group <em>and</em> this group as parent of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addChild(Entity t) {
        t.addParent(this)
        children.add(t)
        t
    }
 
    public boolean removeChild(Entity child) {
        children.remove child
    }
}