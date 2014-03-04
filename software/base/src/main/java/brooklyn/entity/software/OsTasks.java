package brooklyn.entity.software;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.io.CharStreams;

/**
 * @since 0.7.0
 */
@Beta
public class OsTasks {

    private static final Logger LOG = LoggerFactory.getLogger(OsTasks.class);

    /** check that needed resources are on classpath, fail fast if not 
     * (e.g. IDE hasn't pulled in resources properly, make it easy to fail before provisioning) */
    public static void checkResourcesValid() {
        Streams.closeQuietly( getOsDetailsScript() );
    }
    
    private static InputStream getOsDetailsScript() {
        return new ResourceUtils(OsTasks.class).getResourceFromUrl("classpath://brooklyn/entity/software/os-details.sh");
    }

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
     *         non-null name, arch and version. If a field can't be determined it will be "unknown".
     */
    public static Task<OsDetails> getOsDetails(final SshMachineLocation sshLocation) {
        return Tasks.<OsDetails>builder()
                .name("Get OsDetails for " + sshLocation)
                .body(new Callable<OsDetails>() {
                    @Override
                    public OsDetails call() throws Exception {
                        // Read os-details script line by line
                        BufferedReader reader = new BufferedReader(Streams.reader(
                                getOsDetailsScript()));
                        List<String> script = CharStreams.readLines(reader);
                        reader.close();

                        // Output expected to be <name>\n<version>\n<architecture>
                        String output = DynamicTasks.queue(SshEffectorTasks.ssh(script)
                                .machine(sshLocation)
                                .requiringZeroAndReturningStdout())
                                .get();
                        String[] details = output.split("\\r?\\n");

                        if (details.length != 3)
                            LOG.warn("Unexpected length ({}) of os details for {}: {}",
                                    new Object[]{details.length, sshLocation, output});

                        String name = find(details, "name:").or("unknown");
                        String version = find(details, "version:").or("unknown");
                        String architecture = find(details, "architecture:").or("unknown");

                        OsDetails osDetails = new BasicOsDetails(name, architecture, version);
                        if (LOG.isDebugEnabled())
                            LOG.debug("OsDetails for {}: {}", sshLocation, osDetails);
                        return osDetails;
                    }

                    private Optional<String> find(String[] inputs, String field) {
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
                })
                .build();
    }

}
