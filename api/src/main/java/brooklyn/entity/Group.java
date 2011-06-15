package brooklyn.entity;

import java.util.Collection;

public interface Group extends Entity {
    Collection<Entity> getChildren();
    public Entity addChild(Entity child);
    public boolean removeChild(Entity child);
}
