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

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7ServerImpl;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7SshDriver;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.os.Os;

import com.google.common.net.HostAndPort;

/**
 * For simulating various aspects of the JBoss 7 app-server entity.
 *  
 * The use-case for this is that the desired configuration is not always available for testing. 
 * For example, there may be insufficient resources to run 100s of JBoss app-servers, or one 
 * may be experimenting with possible configurations such as use of an external monitoring tool 
 * that is not yet available.
 * 
 * It is then possible to simulate aspects of the behaviour, for performance and load testing purposes. 
 * 
 * There is configuration for:
 * <ul>
 *   <li>{@code simulateEntity}
 *     <ul>
 *       <li>if true, no underlying entity will be started. Instead a sleep 100000 job will be run and monitored.
 *       <li>if false, the underlying entity (i.e. a JBoss app-server) will be started as normal.
 *     </ul>
 *   <li>{@code simulateExternalMonitoring}
 *     <ul>
 *       <li>if true, disables the default monitoring mechanism. Instead, a function will periodically execute 
 *           to set the entity's sensors (as though the values had been obtained from the external monitoring tool).
 *       <li>if false, then:
 *         <ul>
 *           <li>If {@code simulateEntity==true} it will execute comparable commands (e.g. execute a command of the same 
 *               size over ssh or do a comparable number of http GET requests).
 *           <li>If {@code simulateEntity==false} then normal monitoring will be done.
 *         </ul>
 *     </ul>
 *   <li>{@code skipSshOnStart}
 *     <ul>
 *       <li>If true (and if {@code simulateEntity==true}), then no ssh commands will be executed at deploy-time. 
 *           This is useful for speeding up load testing, to get to the desired number of entities.
 *           Should not be set to {@code true} if {@code simulateEntity==false}.
 *       <li>If false, the ssh commands will be executed (based on the value of {@code simulateEntity}.
 *     </ul>
 * </ul>
 */
public class SimulatedJBoss7ServerImpl extends JBoss7ServerImpl {

    public static final ConfigKey<Boolean> SIMULATE_ENTITY = SimulatedTheeTierApp.SIMULATE_ENTITY;
    public static final ConfigKey<Boolean> SIMULATE_EXTERNAL_MONITORING = SimulatedTheeTierApp.SIMULATE_EXTERNAL_MONITORING;
    public static final ConfigKey<Boolean> SKIP_SSH_ON_START = SimulatedTheeTierApp.SKIP_SSH_ON_START;
    
    private HttpFeed httpFeed;
    private FunctionFeed functionFeed;
    
    @Override
    public Class<?> getDriverInterface() {
        return SimulatedJBoss7SshDriver.class;
    }

    @Override
    protected void connectSensors() {
        boolean simulateEntity = getConfig(SIMULATE_ENTITY);
        boolean simulateExternalMonitoring = getConfig(SIMULATE_EXTERNAL_MONITORING);

        if (!simulateEntity && !simulateExternalMonitoring) {
            super.connectSensors();
            return;
        }
        
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this,
                getAttribute(MANAGEMENT_HTTP_PORT) + getConfig(PORT_INCREMENT));

        String managementUri = String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource",
                hp.getHostText(), hp.getPort());
        setAttribute(MANAGEMENT_URL, managementUri);

        if (simulateExternalMonitoring) {
            // TODO What would set this normally, if not doing connectServiceUpIsRunning?
            setAttribute(SERVICE_PROCESS_IS_RUNNING, true);
        } else {
            // if simulating entity, then simulate work of periodic HTTP request; TODO but not parsing JSON response
            String uriToPoll = (simulateEntity) ? "http://localhost:8081" : managementUri;
            
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri(uriToPoll)
                    .credentials(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Integer>(MANAGEMENT_STATUS)
                            .onSuccess(HttpValueFunctions.responseCode()))
                    .build();
            
            // Polls over ssh for whether process is running
            connectServiceUpIsRunning();
        }
        
        functionFeed = FunctionFeed.builder()
                .entity(this)
                .period(5000)
                .poll(new FunctionPollConfig<Boolean,Boolean>(MANAGEMENT_URL_UP)
                        .callable(new Callable<Boolean>() {
                            private int counter = 0;
                            public Boolean call() {
                                setAttribute(REQUEST_COUNT, (counter++ % 100));
                                setAttribute(ERROR_COUNT, (counter++ % 100));
                                setAttribute(TOTAL_PROCESSING_TIME, (counter++ % 100));
                                setAttribute(MAX_PROCESSING_TIME, (counter++ % 100));
                                setAttribute(BYTES_RECEIVED, (long) (counter++ % 100));
                                setAttribute(BYTES_SENT, (long) (counter++ % 100));
                                return true;
                            }}))
                .build();
        
        addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                .from(MANAGEMENT_URL_UP)
                .computing(Functionals.ifNotEquals(true).value("Management URL not reachable") )
                .build());
    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null) httpFeed.stop();
        if (functionFeed != null) functionFeed.stop();
    }
    
    public static class SimulatedJBoss7SshDriver extends JBoss7SshDriver {
        public SimulatedJBoss7SshDriver(SimulatedJBoss7ServerImpl entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public boolean isRunning() {
            if (entity.getConfig(SKIP_SSH_ON_START)) {
                return true;
            } else {
                return super.isRunning();
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
        
        @Override
        public void customize() {
            if (entity.getConfig(SKIP_SSH_ON_START)) {
                // no-op
            } else {
                super.customize();
            }
        }
        
        @Override
        public void launch() {
            if (!entity.getConfig(SIMULATE_ENTITY)) {
                super.launch();
                return;
            }
            
            // We wait for evidence of JBoss running because, using SshCliTool,
            // we saw the ssh session return before the JBoss process was fully running
            // so the process failed to start.
            String pidFile = Os.mergePathsUnix(getRunDir(), PID_FILENAME);

            if (entity.getConfig(SKIP_SSH_ON_START)) {
                // minimal ssh, so that isRunning will subsequently work
                newScript(MutableMap.of("usePidFile", pidFile), LAUNCHING)
                        .body.append(
                                format("nohup sleep 100000 > %s/console 2>&1 < /dev/null &", getRunDir()))
                        .execute();
            } else {
                newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                        .body.append(
                                "export LAUNCH_JBOSS_IN_BACKGROUND=true",
                                format("export JBOSS_HOME=%s", getExpandedInstallDir()),
                                format("export JBOSS_PIDFILE=%s/%s", getRunDir(), PID_FILENAME),
                                format("echo skipping exec of %s/bin/%s.sh ", getExpandedInstallDir(), SERVER_TYPE) +
                                        format("--server-config %s ", CONFIG_FILE) +
                                        format("-Djboss.server.base.dir=%s/%s ", getRunDir(), SERVER_TYPE) +
                                        format("\"-Djboss.server.base.url=file://%s/%s\" ", getRunDir(), SERVER_TYPE) +
                                        "-Djava.net.preferIPv4Stack=true " +
                                        "-Djava.net.preferIPv6Addresses=false " +
                                        format(" >> %s/console 2>&1 </dev/null &", getRunDir()),
                                format("nohup sleep 100000 > %s/console 2>&1 < /dev/null &", getRunDir()),
                                format("echo $! > "+pidFile),
                                format("echo starting > %s/console", getRunDir()),
                                "for i in {1..10}\n" +
                                        "do\n" +
                                        "    grep -i 'starting' "+getRunDir()+"/console && exit\n" +
                                        "    sleep 1\n" +
                                        "done\n" +
                                        "echo \"Couldn't determine if process is running (console output does not contain 'starting'); continuing but may subsequently fail\""
    
                            )
                        .execute();
            }
        }
    }
}
