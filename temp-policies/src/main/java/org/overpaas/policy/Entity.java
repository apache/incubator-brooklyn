package org.overpaas.policy;

import org.overpaas.activity.Event;
import org.overpaas.activity.EventFilter;
import org.overpaas.activity.EventListener;
import org.overpaas.activity.NestedMapAccessor;

import com.google.common.base.Predicate;

public interface Entity {

    NestedMapAccessor getMetrics();
    
    void subscribe(EventFilter filter, EventListener listener);
    
    void subscribeToChildren(Predicate<Entity> childFilter, EventFilter eventFilter, EventListener listener);

    void raiseEvent(Event event);
}
