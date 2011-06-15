package brooklyn.entity;

import java.util.Collection
import java.util.Map
import java.util.concurrent.CopyOnWriteArraySet

public interface Group extends Entity {
    Collection<Entity> getChildren();
    public Entity addChild(Entity child);
    public boolean removeChild(Entity child);
}

public abstract class AbstractGroup extends AbstractEntity implements Group {
    public AbstractGroup(Map props=[:], Group parent=null) {
        super(props, parent)
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