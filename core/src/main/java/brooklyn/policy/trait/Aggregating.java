package brooklyn.policy.trait;

import brooklyn.entity.Entity;

public interface Aggregating<T> {
    
    public void addProducer(Entity producer);
    public T removeProducer(Entity producer);

}
