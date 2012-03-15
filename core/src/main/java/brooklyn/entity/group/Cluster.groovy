package brooklyn.entity.group

import java.util.Collection
import java.util.List
import java.util.Map
import java.util.concurrent.Future

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.EntityStartUtils

/**
 * Intended to represent a group of homogeneous entities in a single location.
 */
public interface Cluster extends Group, Startable, Resizable {
    @SetFromFlag('initialSize')
    BasicConfigKey<Integer> INITIAL_SIZE = [ Integer, "cluster.initial.size", "Initial cluster size", 0 ]
}

