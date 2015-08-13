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
package brooklyn.policy.ha;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Catalog(name="Ssh Connectivity Failure Detector", description="HA policy for monitoring an SshMachine, "
        + "emitting an event if the connection is lost/restored")
public class SshMachineFailureDetector extends AbstractFailureDetector {
    private static final Logger LOG = LoggerFactory.getLogger(SshMachineFailureDetector.class);
    public static final String DEFAULT_UNIQUE_TAG = "failureDetector.sshMachine.tag";

    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_FAILED = HASensors.CONNECTION_FAILED;

    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_RECOVERED = HASensors.CONNECTION_RECOVERED;

    public static final ConfigKey<Duration> CONNECT_TIMEOUT = ConfigKeys.newDurationConfigKey(
            "ha.sshConnection.timeout", "How long to wait for conneciton before declaring failure", Duration.TEN_SECONDS);

    @Override
    public void init() {
        super.init();
        if (config().getRaw(SENSOR_FAILED).isAbsent()) {
            config().set(SENSOR_FAILED, CONNECTION_FAILED);
        }
        if (config().getRaw(SENSOR_RECOVERED).isAbsent()) {
            config().set(SENSOR_RECOVERED, CONNECTION_RECOVERED);
        }
        if (config().getRaw(POLL_PERIOD).isAbsent()) {
            config().set(POLL_PERIOD, Duration.ONE_MINUTE);
        }
        uniqueTag = DEFAULT_UNIQUE_TAG;
    }

    @Override
    protected CalculatedStatus calculateStatus() {
        Maybe<SshMachineLocation> sshMachineOption = Machines.findUniqueSshMachineLocation(entity.getLocations());
        if (sshMachineOption.isPresent()) {
            SshMachineLocation sshMachine = sshMachineOption.get();
            try {
                Duration timeout = config().get(CONNECT_TIMEOUT);
                Map<String, ?> flags = ImmutableMap.of(
                        SshTool.PROP_CONNECT_TIMEOUT.getName(), timeout.toMilliseconds(),
                        SshTool.PROP_SESSION_TIMEOUT.getName(), timeout.toMilliseconds(),
                        SshTool.PROP_SSH_TRIES.getName(), 1);
                int exitCode = sshMachine.execCommands(flags, SshMachineFailureDetector.class.getName(), ImmutableList.of("exit"));
                return new BasicCalculatedStatus(exitCode == 0, sshMachine.toString());
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                boolean isFirstFailure = lastPublished != LastPublished.FAILED && currentFailureStartTime == null;
                if (isFirstFailure) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed connecting to machine " + sshMachine, e);
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Failed connecting to machine " + sshMachine, e);
                    }
                }
                return new BasicCalculatedStatus(false, e.getMessage());
            }
        } else {
            return new BasicCalculatedStatus(true, "no machine started, not complaining");
        }
    }
}
