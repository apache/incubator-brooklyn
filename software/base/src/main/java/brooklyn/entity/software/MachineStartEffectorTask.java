package brooklyn.entity.software;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EffectorBody;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/** Abstract Task which can be used as a basis for starting entities,
 * in such a way that machines will get provisioned as needed */
public abstract class MachineStartEffectorTask extends EffectorBody<Void> {
    
    private static final Logger log = LoggerFactory.getLogger(MachineStartEffectorTask.class);

    // TODO polymorphic parametrisation of effetor, take LOCATION, take strings, etc
    @SuppressWarnings("serial")
    public static final ConfigKey<Collection<? extends Location>> LOCATIONS =
            ConfigKeys.newConfigKey(new TypeToken<Collection<? extends Location>>() {}, "locations", 
            "locations where the entity should be started");

    @Override
    public Void main(ConfigBag parameters) {
        Collection<? extends Location> locations = parameters.get(LOCATIONS);
        Preconditions.checkNotNull(locations, "locations");
        entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
        try {
            startInLocationsAsync(locations);
            waitForLast();
            if (entity().getAttribute(Attributes.SERVICE_STATE) == Lifecycle.STARTING) 
                entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Throwable t) {
            entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
        return null;
    }
    
    protected void startInLocationsAsync(Collection<? extends Location> locations) {
        if (locations.isEmpty()) locations = entity().getLocations();
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Expected one non-null location when starting "+this+", but given "+locations);
            
        startInLocationAsync( Iterables.getOnlyElement(locations) );
    }

    protected void startInLocationAsync(Location location) {
        if (location instanceof MachineProvisioningLocation) {
            Task<MachineLocation> machineTask = provisionAsync((MachineProvisioningLocation<?>)location);
            startInMachineLocationAsync(Tasks.supplier(machineTask));
        } else if (location instanceof MachineLocation) {
            startInMachineLocationAsync(Suppliers.ofInstance((MachineLocation)location));
        } else {
            throw new IllegalArgumentException("Unsupported location "+location+", when starting "+this);
        }
    }

    protected final Task<MachineLocation> provisionAsync(final MachineProvisioningLocation<?> location) {
        return DynamicTasks.queue(Tasks.<MachineLocation>builder().name("provisioning ("+location.getDisplayName()+")").body(
                new Callable<MachineLocation>() {
                    public MachineLocation call() throws Exception {
                        final Map<String,Object> flags = obtainProvisioningFlags(location);
                        if (!(location instanceof LocalhostMachineProvisioningLocation))
                            log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[] {this, location, flags.get("inboundPorts")});
                        entity().setAttribute(SoftwareProcess.PROVISIONING_LOCATION, location);
                        MachineLocation machine;
                        try {
                            machine = Tasks.withBlockingDetails("Provisioning machine in "+location, new Callable<MachineLocation>() {
                                public MachineLocation call() throws NoMachinesAvailableException {
                                    return location.obtain(flags);
                                }});
                            if (machine == null) throw new NoMachinesAvailableException("Failed to obtain machine in "+location.toString());
                        } catch (Exception e) {
                            throw Exceptions.propagate(e);
                        }
                        
                        if (log.isDebugEnabled())
                            log.debug("While starting {}, obtained new location instance {}", this, 
                                    (machine instanceof SshMachineLocation ? 
                                            machine+", details "+((SshMachineLocation)machine).getUser()+":"+Entities.sanitize(((SshMachineLocation)machine).getAllConfig(false)) 
                                            : machine));
                        if (!(location instanceof LocalhostMachineProvisioningLocation))
                            log.info("While starting {}, obtained a new location instance {}, now preparing process there", this, machine);
                        return machine;
                    }
                }).build());
    }

    protected abstract Map<String, Object> obtainProvisioningFlags(final MachineProvisioningLocation<?> location);

    protected abstract void startInMachineLocationAsync(final Supplier<MachineLocation> machineS);
}

