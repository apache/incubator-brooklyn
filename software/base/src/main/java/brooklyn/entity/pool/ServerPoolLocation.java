package brooklyn.entity.pool;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.flags.SetFromFlag;

public class ServerPoolLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<ServerPool, ServerPoolLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolLocation.class);

    @SetFromFlag("owner")
    public static final ConfigKey<ServerPool> OWNER = ConfigKeys.newConfigKey(
            ServerPool.class, "pool.location.owner");

    @Override
    public void init() {
        LOG.debug("Initialising. Owner is: {}", checkNotNull(getConfig(OWNER), OWNER.getName()));
        super.init();
    }

    @Override
    public ServerPool getOwner() {
        return getConfig(OWNER);
    }

    @Override
    public MachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        // Call server pool and try to obtain one of its machines
        return getOwner().claimMachine(flags);
    }

    @Override
    public MachineProvisioningLocation<MachineLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void release(MachineLocation machine) {
        getOwner().releaseMachine(machine);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.newLinkedHashMap();
    }
}
