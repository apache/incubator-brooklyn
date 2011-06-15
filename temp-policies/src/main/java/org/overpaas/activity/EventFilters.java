package org.overpaas.activity;

public class EventFilters {
    private EventFilters() {}
    
    public static EventFilter newMetricFilter(final String[] keySegments) {
        return new EventFilter() {
            public boolean apply(Event val) {
                return EventDictionary.ATTRIBUTE_CHANGED_EVENT_NAME.equals(val.getType()) && val.getMetrics().getRaw(keySegments) != null;
            }
        };
    }
    
    public static EventFilter all() {
        return new EventFilter() {
            public boolean apply(Event val) {
                return true;
            }
        };
    }
}
