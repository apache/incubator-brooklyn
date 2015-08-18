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
package org.apache.brooklyn.policy.ha;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.event.Sensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.flags.SetFromFlag;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;

import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.net.HostAndPort;

/**
 * Monitors a given {@link HostAndPort}, to emit HASensors.CONNECTION_FAILED and HASensors.CONNECTION_RECOVERED 
 * if the connection is lost/restored.
 */
@Catalog(name="Connection Failure Detector", description="HA policy for monitoring a host:port, "
        + "emitting an event if the connection is lost/restored")
public class ConnectionFailureDetector extends AbstractFailureDetector {

    public static final ConfigKey<HostAndPort> ENDPOINT = ConfigKeys.newConfigKey(HostAndPort.class, "connectionFailureDetector.endpoint");

    public static final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(Duration.class, "connectionFailureDetector.pollPeriod", "", Duration.ONE_SECOND);

    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_FAILED = HASensors.CONNECTION_FAILED;

    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_RECOVERED = HASensors.CONNECTION_RECOVERED;

    @SetFromFlag("connectionFailedStabilizationDelay")
    public static final ConfigKey<Duration> CONNECTION_FAILED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("connectionFailureDetector.serviceFailedStabilizationDelay")
            .description("Time period for which the connection must be consistently down for "
                    + "(e.g. doesn't report down-up-down) before concluding failure. "
                    + "Note that long TCP timeouts mean there can be long (e.g. 70 second) "
                    + "delays in noticing a connection refused condition.")
            .defaultValue(Duration.ZERO)
            .build();

    @SetFromFlag("connectionRecoveredStabilizationDelay")
    public static final ConfigKey<Duration> CONNECTION_RECOVERED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("connectionFailureDetector.serviceRecoveredStabilizationDelay")
            .description("For a failed connection, time period for which the connection must be consistently up for (e.g. doesn't report up-down-up) before concluding recovered")
            .defaultValue(Duration.ZERO)
            .build();

    @Override
    public void init() {
        super.init();
        getRequiredConfig(ENDPOINT); // just to confirm it's set, failing fast
        if (config().getRaw(SENSOR_FAILED).isAbsent()) {
            config().set(SENSOR_FAILED, CONNECTION_FAILED);
        }
        if (config().getRaw(SENSOR_RECOVERED).isAbsent()) {
            config().set(SENSOR_RECOVERED, CONNECTION_RECOVERED);
        }
    }

    @Override
    protected CalculatedStatus calculateStatus() {
        HostAndPort endpoint = getConfig(ENDPOINT);
        boolean isHealthy = Networking.isReachable(endpoint);
        return new BasicCalculatedStatus(isHealthy, "endpoint=" + endpoint);
    }

    //Persistence compatibility overrides
    @Override
    protected Duration getPollPeriod() {
        return getConfig(POLL_PERIOD);
    }

    @Override
    protected Duration getFailedStabilizationDelay() {
        return getConfig(CONNECTION_FAILED_STABILIZATION_DELAY);
    }

    @Override
    protected Duration getRecoveredStabilizationDelay() {
        return getConfig(CONNECTION_RECOVERED_STABILIZATION_DELAY);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Sensor<FailureDescriptor> getSensorFailed() {
        Maybe<Object> sensorFailed = config().getRaw(SENSOR_FAILED);
        if (sensorFailed.isPresent()) {
            return (Sensor<FailureDescriptor>)sensorFailed.get();
        } else {
            return CONNECTION_FAILED;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Sensor<FailureDescriptor> getSensorRecovered() {
        Maybe<Object> sensorRecovered = config().getRaw(SENSOR_RECOVERED);
        if (sensorRecovered.isPresent()) {
            return (Sensor<FailureDescriptor>)sensorRecovered.get();
        } else {
            return CONNECTION_RECOVERED;
        }
    }

}
