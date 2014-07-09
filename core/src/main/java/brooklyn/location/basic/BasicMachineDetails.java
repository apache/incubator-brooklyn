/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.HardwareDetails;
import brooklyn.location.MachineDetails;
import brooklyn.location.OsDetails;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.ssh.internal.PlainSshExecTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;

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
        return TaskTags.markInessential(DynamicTasks.queueIfPossible(taskForSshMachineLocation(location))
                .orSubmitAsync()
                .asTask())
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

                Map<String,String> details = Maps.newHashMap(Splitter.on(CharMatcher.anyOf("\r\n"))
                        .omitEmptyStrings()
                        .withKeyValueSeparator(":")
                        .split(stdout));

                String name = details.remove("name");
                String version = details.remove("version");
                String architecture = details.remove("architecture");
                Integer ram = intOrNull(details, "ram");
                Integer cpuCount = intOrNull(details, "cpus");
                if (!details.isEmpty()) {
                    LOG.debug("Unused keys from os-details script: " + Joiner.on(", ").join(details.keySet()));
                }

                OsDetails osDetails = new BasicOsDetails(name, architecture, version);
                HardwareDetails hardwareDetails = new BasicHardwareDetails(cpuCount, ram);
                BasicMachineDetails machineDetails = new BasicMachineDetails(hardwareDetails, osDetails);

                if (LOG.isDebugEnabled())
                    LOG.debug("Machine details for {}: {}", location, machineDetails);

                return machineDetails;
            }

            private Integer intOrNull(Map<String, String> details, String key) {
                try {
                    return Integer.valueOf(details.remove(key));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };
    }

}
