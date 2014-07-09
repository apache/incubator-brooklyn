package brooklyn.entity.group;

import brooklyn.entity.Group;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * Intended to represent a group of homogeneous entities in a single location.
 */
public interface Cluster extends Group, Startable, Resizable {
    
    @SetFromFlag("initialSize")
    BasicConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "cluster.initial.size", "Initial cluster size", 1);
}
