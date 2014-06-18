package brooklyn.entity.pool;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.MachineLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.dynamic.LocationOwner;

/**
 * A preallocated server pool is an entity that other applications can deploy to.
 * Behaving as a cluster, the machines it creates for its members are reused.
 * <p/>
 * Notes:
 * <ul>
 *     <li>
 *         The pool does not configure ports appropriately for applications subsequently
 *         deployed. If an entity that is to be run in the pool requires any ports open
 *         other than port 22 then that port should be configured on the {@link #MEMBER_SPEC}
 *         of the pool. This is a limitation of Brooklyn that will be addressed in a
 *         future release.
 *     </li>
 * </ul>
 */
@ImplementedBy(ServerPoolImpl.class)
public interface ServerPool extends DynamicCluster, LocationOwner<ServerPoolLocation, ServerPool> {

    ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 2);

    AttributeSensor<Integer> AVAILABLE_COUNT = Sensors.newIntegerSensor(
            "pool.available", "The number of locations in the pool that are unused");

    AttributeSensor<Integer> CLAIMED_COUNT = Sensors.newIntegerSensor(
            "pool.claimed", "The number of locations in the pool that are in use");

    ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.MEMBER_SPEC,
            EntitySpec.create(MachineEntity.class));

    public MachineLocation claimMachine(Map<?, ?> flags) throws NoMachinesAvailableException;

    public void releaseMachine(MachineLocation machine);

}
