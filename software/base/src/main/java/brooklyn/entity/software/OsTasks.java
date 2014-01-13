package brooklyn.entity.software;

import java.io.BufferedReader;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import brooklyn.entity.Entity;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicOsDetails;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

/**
 * @since 0.7.0
 */
@Beta
public class OsTasks {

    public static final Logger LOG = LoggerFactory.getLogger(OsTasks.class);

    /**
     * Delegates to {@link #getOsDetails(SshMachineLocation)} using the given entity's location.
     * @throws IllegalStateException if given entity has no {@link SshMachineLocation}
     */
    public static Task<OsDetails> getOsDetails(final Entity entity) {
        Optional<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalStateException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        return getOsDetails(location.get());
    }

    /**
     * Gather {@link OsDetails operating system details} about the given SSH location.
     * @param sshLocation Location in question.
     * @return A {@link Task} returning an {@link OsDetails} instance that is guaranteed to have
     *         non-null name, arch and version. If name and version can't be determined they will
     *         be "unknown".
     */
    public static Task<OsDetails> getOsDetails(final SshMachineLocation sshLocation) {
        return Tasks.<OsDetails>builder()
                .name("Get OsDetails for " + sshLocation)
                .body(new Callable<OsDetails>() {
                    @Override
                    public OsDetails call() throws Exception {
                        // Read os-details script line by line
                        BufferedReader reader = new BufferedReader(Streams.reader(
                                new ResourceUtils(this).getResourceFromUrl("classpath://brooklyn/entity/software/os-and-version.sh")));
                        List<String> script = CharStreams.readLines(reader);
                        reader.close();

                        // Output expected to be <name>\n<version>\n<architecture>
                        String[] nameAndVersion = DynamicTasks.queue(SshEffectorTasks.ssh(script)
                                .machine(sshLocation)
                                .requiringZeroAndReturningStdout())
                                .get()
                                .split("\\r?\\n");

                        String name = nameAndVersion.length > 0 ? nameAndVersion[0] : "unknown";
                        String version = nameAndVersion.length > 1 ? nameAndVersion[1] : "unknown";
                        String architecture = nameAndVersion.length > 2 ? nameAndVersion[2] : "unknown";
                        OsDetails details = new BasicOsDetails(name, architecture, version);
                        if (LOG.isDebugEnabled())
                            LOG.debug("OsDetails for {}: {}", sshLocation, details);
                        return details;
                    }
                })
                .build();

    }

}
