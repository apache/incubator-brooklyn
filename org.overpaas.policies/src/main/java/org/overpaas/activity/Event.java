package org.overpaas.activity;


public interface Event {
    public String getType();
    public NestedMapAccessor getMetrics();
}
