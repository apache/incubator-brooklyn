package brooklyn.entity;

import java.beans.PropertyChangeListener;
import java.util.Collection;

public interface Application extends Entity {
	void registerEntity(Entity entity);
	Collection<Entity> getEntities();
    void addEntityChangeListener(PropertyChangeListener listener);
}
