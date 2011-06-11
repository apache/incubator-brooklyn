package org.overpaas.entities;

import java.util.Collection;
import java.util.Map;

public interface Group extends Entity {
    Collection<Entity> getChildren();
    public Entity addChild(Entity child);
    public boolean removeChild(Entity child);
}

//@InheritConstructors
public abstract class AbstractGroup extends AbstractEntity implements Group {
    public AbstractGroup(Map props=[:], Group parent=null) {
        super(props, parent)
    }

    final Collection<Entity> children = Collections.synchronizedCollection(new LinkedHashSet<Entity>())

    /** adds argument as child of this group *and* this group as parent of the child;
     * returns argument passed in, for convenience */
    public Entity addChild(Entity t) {
        t.addParent(this)
        children.add(t)
        t
    }
    public boolean removeChild(Entity child) {
        children.remove child
    }

}