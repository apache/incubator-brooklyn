package brooklyn.event;

import brooklyn.entity.Entity;

public interface Event<T> {
    public Entity getSource();
    public Sensor<T> getSensor();
    public T getValue();
    public void setValue(T value);
}