package brooklyn.entity.software;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;

import brooklyn.entity.Entity;
import brooklyn.location.MachineDetails;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

/**
 * @since 0.7.0
 */
@Beta
public class MachineTasks {

    private static final Logger LOG = LoggerFactory.getLogger(MachineTasks.class);

    /**
     * @return A task that fetches an entity's MachineDetails from its location.
     * @throws IllegalArgumentException if given entity has no {@link SshMachineLocation}
     */
    public static Task<MachineDetails> getMachineDetailsTask(Entity entity) {
        Optional<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalArgumentException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        final SshMachineLocation sshLocation = location.get();
        return Tasks.<MachineDetails>builder()
                .name("Getting MachineDetails for " + sshLocation)
                .body(new Callable<MachineDetails>() {
                    @Override
                    public MachineDetails call() {
                        return sshLocation.getMachineDetails();
                    }
                })
                .build();
    }

    /**
     * @return Fetches an entity's MachineDetails from its location.
     * @throws IllegalArgumentException if given entity has no {@link SshMachineLocation}
     */
    public static MachineDetails getMachineDetails(Entity entity) {
        Optional<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalArgumentException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        return DynamicTasks.queue(getMachineDetailsTask(entity))
                .getUnchecked();
    }

}
