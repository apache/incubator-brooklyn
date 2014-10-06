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
package brooklyn.qa.load;

import static java.lang.String.format;

import java.util.concurrent.Callable;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl;
import brooklyn.entity.webapp.jboss.JBoss7SshDriver;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;
import brooklyn.util.os.Os;

import com.google.common.net.HostAndPort;

public class SimulatedJBoss7ServerImpl extends JBoss7ServerImpl {

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
        boolean simulateExternalMonitoring = getConfig(SIMULATE_EXTERNAL_MONITORING);

        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this,
                getAttribute(MANAGEMENT_HTTP_PORT) + getConfig(PORT_INCREMENT));

        String managementUri = String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource",
                hp.getHostText(), hp.getPort());
        setAttribute(MANAGEMENT_URL, managementUri);

        if (simulateExternalMonitoring) {
            // TODO What would set this normally, if not doing connectServiceUpIsRunning?
            setAttribute(SERVICE_PROCESS_IS_RUNNING, true);
        } else {
            // simulate work of periodic HTTP request; TODO but not parsing JSON response
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri("http://localhost:8081")
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
            // We wait for evidence of JBoss running because, using
            // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
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
