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
package org.apache.brooklyn.entity.database.postgresql;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.entity.salt.SaltConfig;
import org.apache.brooklyn.entity.salt.SaltConfigs;
import org.apache.brooklyn.entity.salt.SaltLifecycleEffectorTasks;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.entity.stock.EffectorStartableImpl;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.database.postgresql.PostgreSqlNode;
import org.apache.brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class PostgreSqlNodeSaltImpl extends EffectorStartableImpl implements PostgreSqlNode, SoftwareProcess {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlNodeSaltImpl.class);

    public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("invokes a script")
            .parameter(ExecuteScriptEffectorBody.SCRIPT)
            .impl(new ExecuteScriptEffectorBody())
            .build();

    private SshFeed feed;

    @Override
    public void init() {
        super.init();
        new SaltPostgreSqlLifecycle().attachLifecycleEffectors(this);
    }

    public static class SaltPostgreSqlLifecycle extends SaltLifecycleEffectorTasks {
        public SaltPostgreSqlLifecycle() {
            usePidFile("/var/run/postgresql/*.pid");
            useService("postgresql");
        }

        @Override
        protected void startMinionAsync() {
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_FORMULAS);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_RUN_LIST);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_LAUNCH_ATTRIBUTES);

            // TODO Set these as defaults, rather than replacing user's value!?
            SaltConfigs.addToFormulas(entity(), "postgres", "https://github.com/saltstack-formulas/postgres-formula/archive/master.tar.gz");
            SaltConfigs.addToRunList(entity(), "postgres");
            SaltConfigs.addLaunchAttributes(entity(), ImmutableMap.<String,Object>builder()
                    .put("port", DependentConfiguration.attributeWhenReady(entity(), PostgreSqlNode.POSTGRESQL_PORT))
                    .put("listen_addresses", "*")
                    .put("pg_hba.type", "host")
                    .put("pg_hba.db", "all")
                    .put("pg_hba.user", "all")
                    .put("pg_hba.addr", "0.0.0.0/0")
                    .put("pg_hba.method", "md5")
                    .build());

            super.startMinionAsync();
        }

        @Override
        protected void postStartCustom() {
            super.postStartCustom();

            // now run the creation script
            String creationScriptUrl = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_URL);
            String creationScript;
            if (creationScriptUrl != null) {
                creationScript = new ResourceUtils(entity()).getResourceAsString(creationScriptUrl);
            } else {
                creationScript = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_CONTENTS);
            }
            entity().invoke(PostgreSqlNodeSaltImpl.EXECUTE_SCRIPT,
                    ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, creationScript).getAllConfig()).getUnchecked();

            // and finally connect sensors
            ((PostgreSqlNodeSaltImpl) entity()).connectSensors();
        }

        @Override
        protected void preStopCustom() {
            ((PostgreSqlNodeSaltImpl) entity()).disconnectSensors();
            super.preStopCustom();
        }
    }

    public static class ExecuteScriptEffectorBody extends EffectorBody<String> {
        public static final ConfigKey<String> SCRIPT = ConfigKeys.newStringConfigKey("script", "contents of script to run");

        @Override
        public String call(ConfigBag parameters) {
            return DynamicTasks.queue(SshEffectorTasks.ssh(
                    BashCommands.pipeTextTo(
                            parameters.get(SCRIPT),
                            BashCommands.sudoAsUser("postgres", "psql --file -")))
                    .requiringExitCodeZero()).getStdout();
        }
    }

    protected void connectSensors() {
        sensors().set(DATASTORE_URL, String.format("postgresql://%s:%s/", getAttribute(HOSTNAME), getAttribute(POSTGRESQL_PORT)));

        Location machine = Iterables.get(getLocations(), 0, null);

        if (machine instanceof SshMachineLocation) {
            feed = SshFeed.builder()
                    .entity(this)
                    .machine((SshMachineLocation)machine)
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command("ps -ef | grep [p]ostgres")
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) %s not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
        }
    }

    protected void disconnectSensors() {
        if (feed != null) feed.stop();
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

    @Override
    public String executeScript(String commands) {
        return Entities.invokeEffector(this, this, EXECUTE_SCRIPT,
                ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, commands).getAllConfig()).getUnchecked();
    }

    @Override
    public void populateServiceNotUpDiagnostics() {
        // TODO no-op currently; should check ssh'able etc
    }
}
