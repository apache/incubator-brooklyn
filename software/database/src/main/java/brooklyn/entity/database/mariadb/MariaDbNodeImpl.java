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
package brooklyn.entity.database.mariadb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;

public class MariaDbNodeImpl extends SoftwareProcessImpl implements MariaDbNode {

    private static final Logger LOG = LoggerFactory.getLogger(MariaDbNodeImpl.class);

    private SshFeed feed;

    @Override
    public Class<?> getDriverInterface() {
        return MariaDbDriver.class;
    }

    @Override
    public MariaDbDriver getDriver() {
        return (MariaDbDriver) super.getDriver();
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
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DATASTORE_URL, String.format("mysql://%s:%s/", getAttribute(HOSTNAME), getAttribute(MARIADB_PORT)));

        /*        
         * TODO status gives us things like:
         *   Uptime: 2427  Threads: 1  Questions: 581  Slow queries: 0  Opens: 53  Flush tables: 1  Open tables: 35  Queries per second avg: 0.239
         * So can extract lots of sensors from that.
         */
        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(getLocations());

        if (machine.isPresent()) {
            String cmd = getDriver().getStatusCmd();
            feed = SshFeed.builder()
                    .entity(this)
                    .period(Duration.FIVE_SECONDS)
                    .machine(machine.get())
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command(cmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .poll(new SshPollConfig<Double>(QUERIES_PER_SECOND_FROM_MARIADB)
                            .command(cmd)
                            .onSuccess(new Function<SshPollValue, Double>() {
                                public Double apply(SshPollValue input) {
                                    String q = Strings.getFirstWordAfter(input.getStdout(), "Queries per second avg:");
                                    return (q == null) ? null : Double.parseDouble(q);
                                }})
                            .setOnFailureOrException(null) )
                    .build();
        } else {
            LOG.warn("Location(s) {} not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
            setAttribute(SERVICE_UP, true);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (feed != null) feed.stop();
        super.disconnectSensors();
    }

    public int getPort() {
        return getAttribute(MARIADB_PORT);
    }

    public String getSocketUid() {
        String result = getAttribute(MariaDbNode.SOCKET_UID);
        if (Strings.isBlank(result))
            setAttribute(MariaDbNode.SOCKET_UID, (result = Identifiers.makeRandomId(6)));
        return result;
    }

    public String getPassword() {
        String result = getAttribute(MariaDbNode.PASSWORD);
        if (Strings.isBlank(result))
            setAttribute(MariaDbNode.PASSWORD, (result = Identifiers.makeRandomId(6)));
        return result;
    }

    @Override
    public String getShortName() {
        return "MariaDB";
    }

    @Override
    public String executeScript(String commands) {
        return getDriver().executeScriptAsync(commands).block().getStdout();
    }

}
