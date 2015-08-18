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
package brooklyn.entity.machine;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EmptySoftwareProcessDriver;
import brooklyn.entity.basic.EmptySoftwareProcessImpl;
import brooklyn.entity.software.SshEffectorTasks;

import org.apache.brooklyn.location.basic.Machines;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.sensor.feed.ssh.SshFeed;
import org.apache.brooklyn.sensor.feed.ssh.SshPollConfig;
import org.apache.brooklyn.sensor.feed.ssh.SshPollValue;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;

public class MachineEntityImpl extends EmptySoftwareProcessImpl implements MachineEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MachineEntityImpl.class);

    static {
        MachineAttributes.init();
    }

    private transient SshFeed sensorFeed;

    @Override
    public void init() {
        LOG.info("Starting server pool machine with id {}", getId());
        super.init();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        // Sensors linux-specific
        if (!getMachine().getMachineDetails().getOsDetails().isLinux()) return;

        sensorFeed = SshFeed.builder()
                .entity(this)
                .period(Duration.THIRTY_SECONDS)
                .poll(new SshPollConfig<Duration>(UPTIME)
                        .command("cat /proc/uptime")
                        .onFailureOrException(Functions.<Duration>constant(null))
                        .onSuccess(new Function<SshPollValue, Duration>() {
                            @Override
                            public Duration apply(SshPollValue input) {
                                return Duration.seconds( Double.valueOf( Strings.getFirstWord(input.getStdout()) ) );
                            }
                        }))
                .poll(new SshPollConfig<Double>(LOAD_AVERAGE)
                        .command("uptime")
                        .onFailureOrException(Functions.constant(-1d))
                        .onSuccess(new Function<SshPollValue, Double>() {
                            @Override
                            public Double apply(SshPollValue input) {
                                String loadAverage = Strings.getFirstWordAfter(input.getStdout(), "load average:").replace(",", "");
                                return Double.valueOf(loadAverage);
                            }
                        }))
                .poll(new SshPollConfig<Double>(CPU_USAGE)
                        .command("cat /proc/stat")
                        .onFailureOrException(Functions.constant(-1d))
                        .onSuccess(new Function<SshPollValue, Double>() {
                            @Override
                            public Double apply(SshPollValue input) {
                                List<String> cpuData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(input.getStdout()));
                                Integer system = Integer.parseInt(cpuData.get(1));
                                Integer user = Integer.parseInt(cpuData.get(3));
                                Integer idle = Integer.parseInt(cpuData.get(4));
                                return (double) (system + user) / (double) (system + user + idle);
                            }
                        }))
                .poll(new SshPollConfig<Long>(USED_MEMORY)
                        .command("free | grep Mem:")
                        .onFailureOrException(Functions.constant(-1L))
                        .onSuccess(new Function<SshPollValue, Long>() {
                            @Override
                            public Long apply(SshPollValue input) {
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(input.getStdout()));
                                return Long.parseLong(memoryData.get(2));
                            }
                        }))
                .poll(new SshPollConfig<Long>(FREE_MEMORY)
                        .command("free | grep Mem:")
                        .onFailureOrException(Functions.constant(-1L))
                        .onSuccess(new Function<SshPollValue, Long>() {
                            @Override
                            public Long apply(SshPollValue input) {
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(input.getStdout()));
                                return Long.parseLong(memoryData.get(3));
                            }
                        }))
                .poll(new SshPollConfig<Long>(TOTAL_MEMORY)
                        .command("free | grep Mem:")
                        .onFailureOrException(Functions.constant(-1L))
                        .onSuccess(new Function<SshPollValue, Long>() {
                            @Override
                            public Long apply(SshPollValue input) {
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(input.getStdout()));
                                return Long.parseLong(memoryData.get(1));
                            }
                        }))
                .build();

    }

    @Override
    public void disconnectSensors() {
        if (sensorFeed != null) sensorFeed.stop();
        super.disconnectSensors();
    }

    @Override
    public Class<?> getDriverInterface() {
        return EmptySoftwareProcessDriver.class;
    }

    public SshMachineLocation getMachine() {
        return Machines.findUniqueSshMachineLocation(getLocations()).get();
    }

    @Override
    public String execCommand(String command) {
        return execCommandTimeout(command, Duration.ONE_MINUTE);
    }

    @Override
    public String execCommandTimeout(String command, Duration timeout) {
        ProcessTaskWrapper<String> task = SshEffectorTasks.ssh(command)
                .environmentVariables(((AbstractSoftwareProcessSshDriver) getDriver()).getShellEnvironment())
                .requiringZeroAndReturningStdout()
                .machine(getMachine())
                .summary(command)
                .newTask();

        try {
            String result = DynamicTasks.queueIfPossible(task)
                    .executionContext(this)
                    .orSubmitAsync()
                    .asTask()
                    .get(timeout);
            return result;
        } catch (TimeoutException te) {
            throw new IllegalStateException("Timed out running command: " + command);
        } catch (Exception e) {
            Integer exitCode = task.getExitCode();
            LOG.warn("Command failed, return code {}: {}", exitCode == null ? -1 : exitCode, task.getStderr());
            throw Exceptions.propagate(e);
        }
    }

}
