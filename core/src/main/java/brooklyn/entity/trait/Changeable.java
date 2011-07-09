package brooklyn.entity.trait;

import groovy.lang.Closure;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import brooklyn.util.internal.SerializableObservableList;
import brooklyn.util.internal.SerializableObservableMap;

/**
 * A collection of entities that can change.
 */
public interface Changeable {
    /**
     * Allows casting a {@link Closure} to a {@link PropertyChangeListener}.
     */
    class ClosurePropertyChangeListener implements PropertyChangeListener {
        Closure<Void> closure;
        public ClosurePropertyChangeListener(Closure<Void> c) { closure = c; }
        public void propertyChange(PropertyChangeEvent event) {
            closure.call(event);
        }
    }

    /**
     * Implement this method to add a {@link PropertyChangeListener} on
     * some collection of entities.
     *
     * The entity collection should be a {@link SerializableObservableList}
     * or a {@link SerializableObservableMap} wrapper.
     */
    public void addEntityChangeListener(PropertyChangeListener listener);
}
