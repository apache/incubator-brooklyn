package brooklyn.management.internal;

import groovy.util.ObservableList

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

public class GroovyObservablesPropertyChangeToCollectionChangeAdapter implements PropertyChangeListener {
    CollectionChangeListener delegate;
    public GroovyObservablesPropertyChangeToCollectionChangeAdapter(CollectionChangeListener delegate) {
        this.delegate = delegate;
    }
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt) {
            case ObservableList.ElementAddedEvent: 
                delegate.onItemAdded(evt.newValue); break;
            case ObservableList.ElementRemovedEvent: 
                delegate.onItemRemoved(evt.newValue); break;
            case ObservableList.ElementUpdatedEvent:
                delegate.onItemRemoved(evt.oldValue); delegate.onItemAdded(evt.newValue); break;
            case ObservableList.ElementClearedEvent:
                evt.values { delegate.onItemRemoved(it); }; break;
            case ObservableList.ElementRemovedEvent:
                evt.values { delegate.onItemRemoved(it); }; break;
            case ObservableList.MultiElementAddedEvent:
                evt.values { delegate.onItemAdded(it); }; break;
        }
    }
    public int hashCode() { delegate.hashCode(); }
    public boolean equals(Object other) {
        if (other instanceof GroovyObservablesPropertyChangeToCollectionChangeAdapter) 
            return delegate.equals(other.delegate);
        if (other instanceof CollectionChangeListener)
            return delegate.equals(other);
        return false;
    }
} 