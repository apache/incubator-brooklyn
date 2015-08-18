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
package org.apache.brooklyn.qa.load;

import static java.lang.String.format;

import java.util.concurrent.Callable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.database.mysql.MySqlNodeImpl;
import brooklyn.entity.database.mysql.MySqlSshDriver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

import org.apache.brooklyn.core.util.task.DynamicTasks;
import org.apache.brooklyn.core.util.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.location.basic.SshMachineLocation;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

/**
 * @see SimulatedJBoss7ServerImpl for description of purpose and configuration options.
 */
public class SimulatedMySqlNodeImpl extends MySqlNodeImpl {

    public static final ConfigKey<Boolean> SIMULATE_ENTITY = SimulatedTheeTierApp.SIMULATE_ENTITY;
    public static final ConfigKey<Boolean> SIMULATE_EXTERNAL_MONITORING = SimulatedTheeTierApp.SIMULATE_EXTERNAL_MONITORING;
    public static final ConfigKey<Boolean> SKIP_SSH_ON_START = SimulatedTheeTierApp.SKIP_SSH_ON_START;
    
    private FunctionFeed feed;
    
    @Override
    public Class<?> getDriverInterface() {
        return SimulatedMySqlSshDriver.class;
    }

    @Override
    protected void connectSensors() {
        boolean simulateExternalMonitoring = getConfig(SIMULATE_EXTERNAL_MONITORING);
        if (simulateExternalMonitoring) {
            setAttribute(DATASTORE_URL, String.format("mysql://%s:%s/", getAttribute(HOSTNAME), getAttribute(MYSQL_PORT)));
            
            feed = FunctionFeed.builder()
                    .entity(this)
                    .period(Duration.FIVE_SECONDS)
                    .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                            .callable(new Callable<Boolean>() {
                                private int counter = 0;
                                public Boolean call() {
                                    setAttribute(QUERIES_PER_SECOND_FROM_MYSQL, (double)(counter++ % 100));
                                    return true;
                                }})
                            .setOnFailureOrException(false))
                    .build();
        } else {
            super.connectSensors();
        }
    }

    public static class SimulatedMySqlSshDriver extends MySqlSshDriver {

        private int counter = 0;
        
        public SimulatedMySqlSshDriver(SimulatedMySqlNodeImpl entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        // simulate metrics, for if using ssh polling
        @Override
        public String getStatusCmd() {
            if (entity.getConfig(SIMULATE_ENTITY)) {
                return "echo Uptime: 2427  Threads: 1  Questions: 581  Slow queries: 0  Opens: 53  Flush tables: 1  Open tables: 35  Queries per second avg: "+(counter++ % 100);
            } else {
                return super.getStatusCmd();
            }
        }

        @Override
        public void install() {
            if (entity.getConfig(SKIP_SSH_ON_START)) {
                // no-op
            } else {
                super.install();
            }
        }
        
        // Not applying creation-script etc, as that requires launching msyqld (so would not scale for single-machine testing)
        // This is a copy of super.customize, but with the mysqladmin-exec disabled
        @Override
        public void customize() {
            if (!entity.getConfig(SIMULATE_ENTITY)) {
                super.customize();
                return;
            } else if (entity.getConfig(SKIP_SSH_ON_START)) {
                // no-op
            } else {
                copyDatabaseConfigScript();
    
                newScript(CUSTOMIZING)
                    .updateTaskAndFailOnNonZeroResultCode()
                    .body.append(
                        "chmod 600 "+getConfigFile(),
                        getBaseDir()+"/scripts/mysql_install_db "+
                            "--basedir="+getBaseDir()+" --datadir="+getDataDir()+" "+
                            "--defaults-file="+getConfigFile())
                    .execute();
    
                // launch, then we will configure it
                launch();
    
                CountdownTimer timer = Duration.seconds(20).countdownTimer();
                boolean hasCreationScript = copyDatabaseCreationScript();
                timer.waitForExpiryUnchecked();
    
                // DELIBERATELY SKIPPED FOR SCALABILITY TESTING ON SINGLE MACHINE
                DynamicTasks.queue(
                    SshEffectorTasks.ssh(
                        "cd "+getRunDir(),
                        "echo skipping exec of "+getBaseDir()+"/bin/mysqladmin --defaults-file="+getConfigFile()+" --password= password "+getPassword()
                    ).summary("setting password"));
    
                if (hasCreationScript)
                    executeScriptFromInstalledFileAsync("creation-script.sql");
    
                // not sure necessary to stop then subsequently launch, but seems safest
                // (if skipping, use a flag in launch to indicate we've just launched it)
                stop();
            }
        }

        @Override
        public void launch() {
            if (!entity.getConfig(SIMULATE_ENTITY)) {
                super.launch();
                return;
            }
            
            entity.setAttribute(MySqlNode.PID_FILE, getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME);
            
            if (entity.getConfig(SKIP_SSH_ON_START)) {
                // minimal ssh, so that isRunning will subsequently work
                newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                        .body.append(
                                format("nohup sleep 100000 > %s 2>&1 < /dev/null &", getLogFile()))
                        .execute();
            } else {
                newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                    .updateTaskAndFailOnNonZeroResultCode()
                    .body.append(format("echo skipping normal exec of nohup %s/bin/mysqld --defaults-file=%s --user=`whoami` > %s 2>&1 < /dev/null &", getBaseDir(), getConfigFile(), getLogFile()))
                    .body.append(format("nohup sleep 100000 > %s 2>&1 < /dev/null &", getLogFile()))
                    .execute();
            }
        }

        @Override
        public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String filenameAlreadyInstalledAtServer) {
            return DynamicTasks.queue(
                    SshEffectorTasks.ssh(
                                    "cd "+getRunDir(),
                                    "echo skipping exec of "+getBaseDir()+"/bin/mysql --defaults-file="+getConfigFile()+" < "+filenameAlreadyInstalledAtServer)
                            .summary("executing datastore script "+filenameAlreadyInstalledAtServer));
        }
    }
}
