package brooklyn.entity.trait;

import java.beans.PropertyChangeListener;

import brooklyn.util.internal.SerializableObservableList;
import brooklyn.util.internal.SerializableObservableMap;

/**
 * A collection of entities that can change.
 */
public interface Changeable {
    /**
     * Implement this method to add a {@link PropertyChangeListener} on
     * some collection of entities.
     *
     * The entity collection should be a {@link SerializableObservableList}
     * or a {@link SerializableObservableMap} wrapper.
     */
    public void addEntityChangeListener(PropertyChangeListener listener);
}
