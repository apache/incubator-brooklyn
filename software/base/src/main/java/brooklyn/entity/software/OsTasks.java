package brooklyn.entity.software;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;

import brooklyn.entity.Entity;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicOsDetails;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.guava.Maybe;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

/**
 * @since 0.7.0
 */
@Beta
public class OsTasks {

    private static final Logger LOG = LoggerFactory.getLogger(OsTasks.class);

    /**
     * Delegates to {@link #getOsDetailsTask(SshMachineLocation)} using the given entity's location.
     * @throws IllegalStateException if given entity has no {@link SshMachineLocation}
     */
    public static Task<OsDetails> getOsDetailsTask(final Entity entity) {
        Maybe<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalStateException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        return getOsDetailsTask(location.get());
    }

    /**
     * Gather {@link OsDetails operating system details} about the given SSH location.
     * @param sshLocation Location in question.
     * @return A {@link Task} that calls {@link #getOsDetails(SshMachineLocation)}.
     */
    public static Task<OsDetails> getOsDetailsTask(final SshMachineLocation sshLocation) {
        return Tasks.<OsDetails>builder()
                .name("Get OsDetails for " + sshLocation)
                .body(new Callable<OsDetails>() {
                    @Override
                    public OsDetails call() {
                        return getOsDetails(sshLocation);
                    }
                })
                .build();
    }

    /**
     * Delegates to {@link #getOsDetails(SshMachineLocation)} using the given entity's location.
     * @throws IllegalStateException if given entity has no {@link SshMachineLocation}
     */
    public static OsDetails getOsDetails(Entity entity) {
        Optional<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalStateException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        return getOsDetails(location.get());
    }

    /**
     * Gather {@link OsDetails operating system details} about the given SSH location immediately.
     * @param sshLocation Location in question.
     * @return An {@link OsDetails} instance that is guaranteed to have non-null name, arch and
     *         version. If a field can't be determined it will be "unknown".
     */
    public static OsDetails getOsDetails(SshMachineLocation sshLocation) {
        BufferedReader reader = new BufferedReader(Streams.reader(
                new ResourceUtils(OsTasks.class).getResourceFromUrl("classpath://brooklyn/entity/software/os-details.sh")));
        List<String> script;

        try {
            script = CharStreams.readLines(reader);
            reader.close();
        } catch (IOException e) {
            LOG.error("Error reading os-details script", e);
            throw Throwables.propagate(e);
        }

        String output = DynamicTasks.queue(SshEffectorTasks.ssh(script)
                .machine(sshLocation)
                .requiringZeroAndReturningStdout())
                .get();
        String[] details = output.split("\\r?\\n");

        if (details.length != 3) {
            LOG.warn("Unexpected length ({}) of os details for {}. Shell script altered but reader not updated?: {}",
                    new Object[]{details.length, sshLocation, output});
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("OsTasks found following details at {}: {}", sshLocation, output);
        }

        String name = find(details, "name:").or("unknown");
        String version = find(details, "version:").or("unknown");
        String architecture = find(details, "architecture:").or("unknown");

        OsDetails osDetails = new BasicOsDetails(name, architecture, version);
        if (LOG.isDebugEnabled())
            LOG.debug("OsDetails for {}: {}", sshLocation, osDetails);
        return osDetails;
    }

    private static Optional<String> find(String[] inputs, String field) {
        for (String input : inputs) {
            if (input.startsWith(field)) {
                String value = input.substring(field.length()).trim();
                return (!value.isEmpty())
                    ? Optional.of(input.substring(field.length()))
                    : Optional.<String>absent();
            }
        }
        return Optional.absent();
    }

}
