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
package brooklyn.entity.database.postgresql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefLifecycleEffectorTasks;
import brooklyn.entity.chef.ChefServerTasks;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;

public class PostgreSqlNodeChefImplFromScratch extends EffectorStartableImpl implements PostgreSqlNode {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlNodeChefImplFromScratch.class);

    public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("invokes a script")
            .parameter(ExecuteScriptEffectorBody.SCRIPT)
            .impl(new ExecuteScriptEffectorBody()).build();
    
    private SshFeed feed;

    public void init() {
        super.init();
        new ChefPostgreSqlLifecycle().attachLifecycleEffectors(this);
    }

    @Override
    public Integer getPostgreSqlPort() { return getAttribute(POSTGRESQL_PORT); }

    @Override
    public String getSharedMemory() { return getConfig(SHARED_MEMORY); }

    @Override
    public Integer getMaxConnections() { return getConfig(MAX_CONNECTIONS); }

    @Override
    public String getShortName() {
        return "PostgreSQL";
    }

    public static class ChefPostgreSqlLifecycle extends ChefLifecycleEffectorTasks {
        {
            usePidFile("/var/run/postgresql/*.pid");
            useService("postgresql");
        }
        protected void startWithKnifeAsync() {
            Entities.warnOnIgnoringConfig(entity(), ChefConfig.CHEF_LAUNCH_RUN_LIST);
            Entities.warnOnIgnoringConfig(entity(), ChefConfig.CHEF_LAUNCH_ATTRIBUTES);
            
            DynamicTasks.queue(
                    ChefServerTasks
                        .knifeConvergeRunList("postgresql::server")
                        .knifeAddAttributes(Jsonya
                            .at("postgresql", "config").add(
                                "port", entity().getPostgreSqlPort(), 
                                "listen_addresses", "*").getRootMap())
                        .knifeAddAttributes(Jsonya
                            .at("postgresql", "pg_hba").list().map().add(
                                "type", "host", "db", "all", "user", "all", 
                                "addr", "0.0.0.0/0", "method", "md5").getRootMap()) 
                        // no other arguments currenty supported; chef will pick a password for us
                );
        }
        protected void postStartCustom() {
            super.postStartCustom();

            // now run the creation script
            String creationScript;
            String creationScriptUrl = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_URL);
            if (creationScriptUrl != null) {
                creationScript = ResourceUtils.create(entity()).getResourceAsString(creationScriptUrl);
            } else {
                creationScript = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_CONTENTS);
            }
            entity().executeScript(creationScript);

            // and finally connect sensors
            entity().connectSensors();
        }
        protected void preStopCustom() {
            entity().disconnectSensors();
            super.preStopCustom();
        }
        protected PostgreSqlNodeChefImplFromScratch entity() {
            return (PostgreSqlNodeChefImplFromScratch) super.entity();
        }
    }
    
    public static class ExecuteScriptEffectorBody extends EffectorBody<String> {
        public static final ConfigKey<String> SCRIPT = ConfigKeys.newStringConfigKey("script", "contents of script to run");
        
        public String call(ConfigBag parameters) {
            return DynamicTasks.queue(SshEffectorTasks.ssh(
                    BashCommands.pipeTextTo(
                        parameters.get(SCRIPT),
                        BashCommands.sudoAsUser("postgres", "psql --file -")))
                    .requiringExitCodeZero()).getStdout();
        }
    }
    
    protected void connectSensors() {
        setAttribute(DATASTORE_URL, String.format("postgresql://%s:%s/", getAttribute(HOSTNAME), getAttribute(POSTGRESQL_PORT)));

        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(getLocations());

        if (machine.isPresent()) {
            feed = SshFeed.builder()
                    .entity(this)
                    .machine(machine.get())
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command("ps -ef | grep [p]ostgres")
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) {} not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
        }
    }

    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }

    @Override
    public String executeScript(String commands) {
        return Entities.invokeEffector(this, this, EXECUTE_SCRIPT,
                ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, commands).getAllConfig()).getUnchecked();
    }

}
