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
package org.apache.brooklyn.entity.database.mysql;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.feed.ssh.SshPollValue;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

public class MySqlNodeImpl extends SoftwareProcessImpl implements MySqlNode {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlNodeImpl.class);

    private SshFeed feed;

    public MySqlNodeImpl() {
    }

    public MySqlNodeImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public MySqlNodeImpl(Map<?,?> flags) {
        super(flags, null);
    }

    public MySqlNodeImpl(Map<?,?> flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class<?> getDriverInterface() {
        return MySqlDriver.class;
    }

    @Override
    public MySqlDriver getDriver() {
        return (MySqlDriver) super.getDriver();
    }
    
    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(EXECUTE_SCRIPT, new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return executeScript((String)parameters.getStringKey("commands"));
            }
        });
        getMutableEntityType().addEffector(MySqlNodeEffectors.EXPORT_DUMP);
        getMutableEntityType().addEffector(MySqlNodeEffectors.IMPORT_DUMP);
        getMutableEntityType().addEffector(MySqlNodeEffectors.CHANGE_PASSWORD);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        sensors().set(DATASTORE_URL, String.format("mysql://%s:%s/", getAttribute(HOSTNAME), getAttribute(MYSQL_PORT)));
        
        /*        
         * TODO status gives us things like:
         *   Uptime: 2427  Threads: 1  Questions: 581  Slow queries: 0  Opens: 53  Flush tables: 1  Open tables: 35  Queries per second avg: 0.239
         * So can extract lots of sensors from that.
         */
        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(getLocations());
        boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);

        if (machine.isPresent()) {
            String cmd = getDriver().getStatusCmd();
            feed = SshFeed.builder()
                    .entity(this)
                    .period(Duration.FIVE_SECONDS)
                    .machine(machine.get())
                    .poll(new SshPollConfig<Double>(QUERIES_PER_SECOND_FROM_MYSQL)
                            .command(cmd)
                            .onSuccess(new Function<SshPollValue, Double>() {
                                @Override
                                public Double apply(SshPollValue input) {
                                    String q = Strings.getFirstWordAfter(input.getStdout(), "Queries per second avg:");
                                    if (q==null) return null;
                                    return Double.parseDouble(q);
                                }})
                            .setOnFailureOrException(null)
                            .enabled(retrieveUsageMetrics))
                    .poll(new SshPollConfig<Boolean>(SERVICE_PROCESS_IS_RUNNING)
                            .command(cmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false)
                            .suppressDuplicates(true))
                    .build();
        } else {
            LOG.warn("Location(s) {} not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
            sensors().set(SERVICE_UP, true);
        }
    }
    
    @Override
    protected void disconnectSensors() {
        if (feed != null) feed.stop();
        super.disconnectSensors();
    }

    public int getPort() {
        return getAttribute(MYSQL_PORT);
    }

    public synchronized String getSocketUid() {
        String result = getAttribute(MySqlNode.SOCKET_UID);
        if (Strings.isBlank(result)) {
            result = Identifiers.makeRandomId(6);
            sensors().set(MySqlNode.SOCKET_UID, result);
        }
        return result;
    }

    public String getPassword() {
        String result = getAttribute(MySqlNode.PASSWORD);
        if (Strings.isBlank(result)) {
            result = Identifiers.makeRandomId(6);
            sensors().set(MySqlNode.PASSWORD, result);
        }
        return result;
    }
    
    @Override
    public String getShortName() {
        return "MySQL";
    }

    @Override
    public String executeScript(String commands) {
        return getDriver().executeScriptAsync(commands).block().getStdout();
    }

}
