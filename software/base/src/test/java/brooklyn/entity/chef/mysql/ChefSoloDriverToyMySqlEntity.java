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
package brooklyn.entity.chef.mysql;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfigs;
import brooklyn.entity.chef.ChefSoloDriver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

@Deprecated /** @deprecated since 0.7.0 use see examples {Dynamic,Typed}ToyMySqlEntityChef */
public class ChefSoloDriverToyMySqlEntity extends SoftwareProcessImpl implements ChefConfig {

    public static final String PID_FILE = "/var/run/mysqld/mysqld.pid";
    public static final ConfigKey<TaskFactory<? extends TaskAdaptable<Boolean>>> IS_RUNNING_TASK =
            ConfigKeys.newConfigKeyWithDefault(ChefSoloDriver.IS_RUNNING_TASK, 
            SshEffectorTasks.isPidFromFileRunning(PID_FILE).runAsRoot());

    public static final ConfigKey<TaskFactory<?>> STOP_TASK =
            ConfigKeys.newConfigKeyWithDefault(ChefSoloDriver.STOP_TASK, 
            SshEffectorTasks.ssh("/etc/init.d/mysql stop").allowingNonZeroExitCode().runAsRoot());

    private SshFeed upFeed;
    
    @Override
    public Class<?> getDriverInterface() {
        return ChefSoloDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        // TODO have a TaskFactoryFeed which reuses the IS_RUNNING_TASK
        upFeed = SshFeed.builder().entity(this).period(Duration.FIVE_SECONDS.toMilliseconds())
            .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                    .command("ps -p `sudo cat /var/run/mysqld/mysqld.pid`")
                    .setOnSuccess(true).setOnFailureOrException(false))
            .build();
    }
    
    @Override
    protected void disconnectSensors() {
        // TODO nicer way to disconnect
        if (upFeed != null) upFeed.stop();
        super.disconnectSensors();
    }
    
    @Override
    public void init() {
        super.init();
        ChefConfigs.addToLaunchRunList(this, "mysql::server");
        ChefConfigs.addToCookbooksFromGithub(this, "mysql", "build-essential", "openssl");
        ChefConfigs.setLaunchAttribute(this, "mysql",  
                MutableMap.of()
                    .add("server_root_password", "MyPassword")
                    .add("server_debian_password", "MyPassword")
                    .add("server_repl_password", "MyPassword")
                );
        
        // TODO other attributes, eg:
        // node['mysql']['port']
    }
    
}
