package org.overpaas.activity.impl;

import java.util.Map;

import org.overpaas.activity.Event;
import org.overpaas.activity.NestedMapAccessor;
import com.google.common.base.Preconditions;

public class EventImpl implements Event {

    private final String type;
    private final NestedMapAccessor metrics;

    public EventImpl(String type, NestedMapAccessor metrics) {
        this.type = Preconditions.checkNotNull(type, "type");
        this.metrics = Preconditions.checkNotNull(metrics, "metrics");
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public EventImpl(String type, Map metrics) {
        this.type = type;
        this.metrics = new NestedMapAccessorImpl(metrics);
    }
    
    @Override public String getType() {
        return type;
    }
    
    @Override public NestedMapAccessor getMetrics() {
        return metrics;
    }
    
    @Override public String toString() {
        return "event("+getType()+", "+getMetrics()+")";
    }
    
    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + type.hashCode();
        result = 31 * result + metrics.hashCode();
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Event)) {
            return false;
        }
        Event o = (Event) obj;
        return type.equals(o.getType()) && metrics.equals(o.getMetrics());
    }
}
