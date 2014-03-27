package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Closer;

import brooklyn.location.HardwareDetails;
import brooklyn.location.MachineDetails;
import brooklyn.location.OsDetails;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ssh.internal.PlainSshExecTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;

@Immutable
public class BasicMachineDetails implements MachineDetails {

    public static final Logger LOG = LoggerFactory.getLogger(BasicMachineDetails.class);

    private final HardwareDetails hardwareDetails;
    private final OsDetails osDetails;

    public BasicMachineDetails(HardwareDetails hardwareDetails, OsDetails osDetails) {
        this.hardwareDetails = checkNotNull(hardwareDetails, "hardwareDetails");
        this.osDetails = checkNotNull(osDetails, "osDetails");
    }

    @Nonnull
    @Override
    public HardwareDetails getHardwareDetails() {
        return hardwareDetails;
    }

    @Nonnull
    @Override
    public OsDetails getOsDetails() {
        return osDetails;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(MachineDetails.class)
                .add("os", osDetails)
                .add("hardware", hardwareDetails)
                .toString();
    }

    /**
     * Creates a MachineDetails for the given location by SSHing to the machine and
     * running a Bash script to gather data. Should only be called from within a
     * task context. If this might not be the case then use {@link
     * #taskForSshMachineLocation(SshMachineLocation)} instead.
     */
    static BasicMachineDetails forSshMachineLocation(SshMachineLocation location) {
        return DynamicTasks.queueIfPossible(taskForSshMachineLocation(location))
                .orSubmitAsync()
                .asTask()
                .getUnchecked();
    }

    /**
     * @return A task that gathers machine details by SSHing to the machine and running
     *         a Bash script to gather data.
     */
    static Task<BasicMachineDetails> taskForSshMachineLocation(SshMachineLocation location) {
        BufferedReader reader = new BufferedReader(Streams.reader(
                new ResourceUtils(BasicMachineDetails.class).getResourceFromUrl(
                        "classpath://brooklyn/location/basic/os-details.sh")));
        List<String> script;
        try {
            script = CharStreams.readLines(reader);
        } catch (IOException e) {
            LOG.error("Error reading os-details script", e);
            throw Throwables.propagate(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // Not rethrowing e because it might obscure an exception caught by the first catch
                LOG.error("Error closing os-details script reader", e);
            }
        }
        Task<BasicMachineDetails> task = new PlainSshExecTaskFactory<String>(location, script)
                .summary("Getting machine details for: " + location)
                .requiringZeroAndReturningStdout()
                .returning(taskToMachineDetailsFunction(location))
                .newTask()
                .asTask();

        return task;
    }

    private static Function<ProcessTaskWrapper<?>, BasicMachineDetails> taskToMachineDetailsFunction(final SshMachineLocation location) {
        return new Function<ProcessTaskWrapper<?>, BasicMachineDetails>() {
            @Override
            public BasicMachineDetails apply(ProcessTaskWrapper<?> input) {
                if (input.getExitCode() != 0) {
                    LOG.warn("Non-zero exit code when fetching machine details for {}; guessing anonymous linux", location);
                    return new BasicMachineDetails(new BasicHardwareDetails(null, null),
                            BasicOsDetails.Factory.ANONYMOUS_LINUX);
                }

                String stdout = input.getStdout();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found following details at {}: {}", location, stdout);
                }

                Iterable<String> details = Splitter.on("\\r?\\n").split(stdout);
                String name = findAndRemove(details, "name:").orNull();
                String version = findAndRemove(details, "version:").orNull();
                String architecture = findAndRemove(details, "architecture:").orNull();
                Integer ram = intOrNull(details, "ram:");
                Integer cpuCount = intOrNull(details, "cpus:");
                if (!Iterables.isEmpty(details)) {
                    LOG.debug("Unused outputs from os-details script: " + Joiner.on(", ").join(details));
                }

                OsDetails osDetails = new BasicOsDetails(name, architecture, version);
                HardwareDetails hardwareDetails = new BasicHardwareDetails(cpuCount, ram);
                BasicMachineDetails machineDetails = new BasicMachineDetails(hardwareDetails, osDetails);

                if (LOG.isDebugEnabled())
                    LOG.debug("Machine details for {}: {}", location, machineDetails);

                return machineDetails;
            }

            // Finds the first entry in the list of inputs starting with field and removes
            // it from inputs.
            private Optional<String> findAndRemove(Iterable<String> inputs, String field) {
                for (Iterator<String> it = inputs.iterator(); it.hasNext(); ) {
                    String input = it.next();
                    if (input.startsWith(field)) {
                        it.remove();
                        String value = input.substring(field.length());
                        return Strings.isNonBlank(value) ? Optional.of(value) : Optional.<String>absent();
                    }
                }
                return Optional.absent();
            }

            private Integer intOrNull(Iterable<String> inputs, String field) {
                Optional<String> f = findAndRemove(inputs, field);
                if (f.isPresent()) {
                    return Integer.valueOf(f.get());
                } else {
                    return null;
                }
            }
        };
    }

}
