package brooklyn.entity.software;

import java.io.BufferedReader;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

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
     * Uses an entity's {@link SshMachineLocation} to gather details about the operating system.
     * @param entity
     * @return A {@link Task} returning an {@link OsDetails} instance that is guaranteed to have
     *         non-null name, arch and version. If name and version can't be determined they will
     *         be "unknown".
     * @throws IllegalStateException if given entity has no {@link SshMachineLocation}
     */
    public static Task<OsDetails> getOsDetails(final Entity entity) {
        Optional<SshMachineLocation> location = Locations.findUniqueSshMachineLocation(entity.getLocations());
        if (!location.isPresent()) {
            throw new IllegalStateException("Entity "+entity+" has no SSH location to gather OS details. Locations: "+entity.getLocations());
        }
        final SshMachineLocation sshLocation = location.get();
        return Tasks.<OsDetails>builder()
                .name("Get OsDetails for " + entity)
                .body(new Callable<OsDetails>() {
                    @Override
                    public OsDetails call() throws Exception {
                        // Survey of CentOS 6.5, Debian Jessie, Fedora 17, OSX and Ubuntu 12.04 suggests
                        // uname -m is the most reliable flag for architecture
                        String architecture = DynamicTasks.queue(SshEffectorTasks.ssh("uname -m")
                                .machine(sshLocation)
                                .requiringZeroAndReturningStdout()).get().trim();

                        // Read os-details script line by line
                        BufferedReader reader = new BufferedReader(Streams.reader(
                                new ResourceUtils(this).getResourceFromUrl("classpath://brooklyn/entity/software/os-and-version.sh")));
                        List<String> script = Lists.newArrayList();
                        for (String line; (line = reader.readLine()) != null; ) {
                            script.add(line);
                        }

                        // Output expected to be <name>\n<version>
                        String[] nameAndVersion = DynamicTasks.queue(SshEffectorTasks.ssh(script.toArray(new String[script.size()]))
                                .machine(sshLocation)
                                .requiringZeroAndReturningStdout())
                                .get()
                                .split("\\r?\\n");

                        String name = nameAndVersion.length > 0 ? nameAndVersion[0] : "unknown";
                        String version = nameAndVersion.length > 1 ? nameAndVersion[1] : "unknown";
                        OsDetails details = new BasicOsDetails(name, architecture, version);
                        if (LOG.isDebugEnabled())
                            LOG.debug("OsDetails for {}: {}", entity, details);
                        return details;
                    }
                })
                .build();

    }

}
