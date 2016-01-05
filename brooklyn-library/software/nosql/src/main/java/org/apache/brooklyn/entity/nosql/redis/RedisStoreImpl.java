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
package org.apache.brooklyn.entity.nosql.redis;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.feed.ssh.SshPollValue;
import org.apache.brooklyn.feed.ssh.SshValueFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * An entity that represents a Redis key-value store service.
 */
public class RedisStoreImpl extends SoftwareProcessImpl implements RedisStore {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RedisStoreImpl.class);

    private transient SshFeed sshFeed;

    public RedisStoreImpl() {
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        // Find an SshMachineLocation for the UPTIME feed
        Optional<Location> location = Iterables.tryFind(getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        if (!location.isPresent()) throw new IllegalStateException("Could not find SshMachineLocation in list of locations");
        SshMachineLocation machine = (SshMachineLocation) location.get();
        String statsCommand = getDriver().getRunDir() + "/bin/redis-cli -p " + getRedisPort() + " info stats";
        boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);
        
        sshFeed = SshFeed.builder()
                .entity(this)
                .machine(machine)
                .period(5, TimeUnit.SECONDS)
                .poll(new SshPollConfig<Integer>(UPTIME)
                        .command(getDriver().getRunDir() + "/bin/redis-cli -p " + getRedisPort() + " info server")
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("uptime_in_seconds"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(TOTAL_CONNECTIONS_RECEIVED)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("total_connections_received"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(TOTAL_COMMANDS_PROCESSED)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("total_commands_processed"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(EXPIRED_KEYS)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("expired_keys"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(EVICTED_KEYS)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("evicted_keys"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(KEYSPACE_HITS)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("keyspace_hits"))
                        .enabled(retrieveUsageMetrics))
                .poll(new SshPollConfig<Integer>(KEYSPACE_MISSES)
                        .command(statsCommand)
                        .onFailureOrException(Functions.constant(-1))
                        .onSuccess(infoFunction("keyspace_misses"))
                        .enabled(retrieveUsageMetrics))
                .build();
    }

    /**
     * Create a {@link Function} to retrieve a particular field value from a {@code redis-cli info}
     * command.
     * 
     * @param field the info field to retrieve and convert
     * @return a new function that converts a {@link SshPollValue} to an {@link Integer}
     */
    private static Function<SshPollValue, Integer> infoFunction(final String field) {
        return Functions.compose(new Function<String, Integer>() {
            @Override
            public Integer apply(@Nullable String input) {
                Optional<String> line = Iterables.tryFind(Splitter.on('\n').split(input), Predicates.containsPattern(field + ":"));
                if (line.isPresent()) {
                    String data = line.get().trim();
                    int colon = data.indexOf(":");
                    return Integer.parseInt(data.substring(colon + 1));
                } else {
                    throw new IllegalStateException("Data for field "+field+" not found: "+input);
                }
            }
        }, SshValueFunctions.stdout());
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (sshFeed != null) sshFeed.stop();
        super.disconnectSensors();
    }

    @Override
    public Class<?> getDriverInterface() {
        return RedisStoreDriver.class;
    }

    @Override
    public RedisStoreDriver getDriver() {
        return (RedisStoreDriver) super.getDriver();
    }

    @Override
    public String getAddress() {
        MachineLocation machine = getMachineOrNull();
        return (machine != null) ? machine.getAddress().getHostAddress() : null;
    }

    @Override
    public Integer getRedisPort() {
        return getAttribute(RedisStore.REDIS_PORT);
    }

}
