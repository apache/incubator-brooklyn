package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.cloud.AbstractAvailabilityZoneExtension;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.ManagementContext;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/** A location which consists of multiple locations stitched together to form availability zones.
 * The first location will be used by default, but if an {@link AvailabilityZoneExtension}-aware entity
 * is used, it may stripe across each of the locations.  See notes at {@link AvailabilityZoneExtension}. */
public class MultiLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T> {

    private static final long serialVersionUID = 7993091317970457862L;
    
    @SuppressWarnings("serial")
    @SetFromFlag("subLocations")
    public static final ConfigKey<List<MachineProvisioningLocation<?>>> SUB_LOCATIONS = ConfigKeys.newConfigKey(
            new TypeToken<List<MachineProvisioningLocation<?>>>() {},
            "subLocations", 
            "The sub-machines that this location can delegate to");
    
    @Override
    public void init() {
        super.init();
        List<MachineProvisioningLocation<?>> subLocs = getRequiredConfig(SUB_LOCATIONS);
        checkState(subLocs.size() >= 1, "sub-locations must not be empty");
        AvailabilityZoneExtension azExtension = new AvailabilityZoneExtensionImpl(getManagementContext(), subLocs);
        addExtension(AvailabilityZoneExtension.class, azExtension);
    }

    /** finds (creates) and returns a {@link MachineLocation}; 
     * currently this only looks at the first sub-location, though that behaviour may change without notice.
     * (if you want striping across locations, see notes in {@link AvailabilityZoneExtension}.) */
    // TODO Could have multiLoc.obtain delegate to loc2 if loc1 has no capacity
    @Override
    public T obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        return firstSubLoc().obtain(flags);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MachineProvisioningLocation<T> newSubLocation(Map<?, ?> newFlags) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(getClass())
                .parent(this)
                .configure(getLocalConfigBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(newFlags));
    }

    @Override
    public void release(T machine) {
        MachineProvisioningLocation<T> subLoc = firstSubLoc();
        subLoc.release(machine);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.<String,Object>newLinkedHashMap();
    }

    @SuppressWarnings("unchecked")
    protected MachineProvisioningLocation<T> firstSubLoc() {
        return (MachineProvisioningLocation<T>) Iterables.get(getRequiredConfig(SUB_LOCATIONS), 0);
    }

    protected <K> K getRequiredConfig(ConfigKey<K> key) {
        return checkNotNull(getConfig(key), key.getName());
    }

    public static class AvailabilityZoneExtensionImpl extends AbstractAvailabilityZoneExtension implements AvailabilityZoneExtension {

        private final List<MachineProvisioningLocation<?>> subLocations;
        
        public AvailabilityZoneExtensionImpl(ManagementContext managementContext, List<MachineProvisioningLocation<?>> subLocations) {
            super(managementContext);
            this.subLocations = ImmutableList.copyOf(subLocations);
        }
        
        @Override
        protected List<Location> doGetAllSubLocations() {
            return ImmutableList.<Location>copyOf(subLocations);
        }
        
        @Override
        protected boolean isNameMatch(Location loc, Predicate<? super String> namePredicate) {
            return namePredicate.apply(loc.getDisplayName());
        }
    }
}
