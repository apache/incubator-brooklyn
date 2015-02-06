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

import java.net.URI;
import java.util.Collection;
import java.util.concurrent.Callable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Group;
import brooklyn.entity.proxy.nginx.NginxControllerImpl;
import brooklyn.entity.proxy.nginx.NginxSshDriver;
import brooklyn.entity.proxy.nginx.UrlMapping;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;

import com.google.common.base.Functions;

/**
 * @see SimulatedJBoss7ServerImpl for description of purpose and configuration options.
 */
public class SimulatedNginxControllerImpl extends NginxControllerImpl {

    public static final ConfigKey<Boolean> SIMULATE_ENTITY = SimulatedTheeTierApp.SIMULATE_ENTITY;
    public static final ConfigKey<Boolean> SIMULATE_EXTERNAL_MONITORING = SimulatedTheeTierApp.SIMULATE_EXTERNAL_MONITORING;
    public static final ConfigKey<Boolean> SKIP_SSH_ON_START = SimulatedTheeTierApp.SKIP_SSH_ON_START;
    
    private HttpFeed httpFeed;
    private FunctionFeed functionFeed;
    
    @Override
    public Class<?> getDriverInterface() {
        return SimulatedNginxSshDriver.class;
    }

    @Override
    public void connectSensors() {
        boolean simulateEntity = getConfig(SIMULATE_ENTITY);
        boolean simulateExternalMonitoring = getConfig(SIMULATE_EXTERNAL_MONITORING);

        if (!simulateEntity && !simulateExternalMonitoring) {
            super.connectSensors();
            return;
        }

        // From AbstractController.connectSensors
        if (getUrl()==null) {
            setAttribute(MAIN_URI, URI.create(inferUrl()));
            setAttribute(ROOT_URL, inferUrl());
        }
        addServerPoolMemberTrackingPolicy();

        // From NginxController.connectSensors
        ConfigToAttributes.apply(this);

        if (!simulateExternalMonitoring) {
            // if simulating entity, then simulate work of periodic HTTP request; TODO but not parsing JSON response
            String uriToPoll = (simulateEntity) ? "http://localhost:8081" : getAttribute(MAIN_URI).toString();
            
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(getConfig(HTTP_POLL_PERIOD))
                    .baseUri(uriToPoll)
                    .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                            .onSuccess(Functions.constant(true))
                            .onFailureOrException(Functions.constant(true)))
                    .build();
        }
        
        functionFeed = FunctionFeed.builder()
                .entity(this)
                .period(getConfig(HTTP_POLL_PERIOD))
                .poll(new FunctionPollConfig<Boolean,Boolean>(SERVICE_UP)
                        .callable(new Callable<Boolean>() {
                            public Boolean call() {
                                return true;
                            }}))
                .build();

        // Can guarantee that parent/managementContext has been set
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings != null) {
            // Listen to the targets of each url-mapping changing
            subscribeToMembers(urlMappings, UrlMapping.TARGET_ADDRESSES, new SensorEventListener<Collection<String>>() {
                    @Override public void onEvent(SensorEvent<Collection<String>> event) {
                        updateNeeded();
                    }
                });

            // Listen to url-mappings being added and removed
            urlMappingsMemberTrackerPolicy = addPolicy(PolicySpec.create(UrlMappingsMemberTrackerPolicy.class)
                    .configure("group", urlMappings));
        }
    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null) httpFeed.stop();
        if (functionFeed != null) functionFeed.stop();
    }

    public static class SimulatedNginxSshDriver extends NginxSshDriver {
        public SimulatedNginxSshDriver(SimulatedNginxControllerImpl entity, SshMachineLocation machine) {
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
            if (!entity.getConfig(SIMULATE_ENTITY)) {
                super.launch();
                return;
            }
            
            Networking.checkPortsValid(MutableMap.of("httpPort", getPort()));

            if (entity.getConfig(SKIP_SSH_ON_START)) {
                // minimal ssh, so that isRunning will subsequently work
                newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                        .body.append(
                                format("mkdir -p %s/logs", getRunDir()),
                                format("nohup sleep 100000 > %s 2>&1 < /dev/null &", getLogFileLocation()))
                        .execute();
            } else {
                newScript(MutableMap.of("usePidFile", false), LAUNCHING)
                        .body.append(
                                format("cd %s", getRunDir()),
                                "echo skipping exec of requireExecutable ./sbin/nginx",
                                sudoBashCIfPrivilegedPort(getPort(), format(
                                        "echo skipping exec of nohup ./sbin/nginx -p %s/ -c conf/server.conf > %s 2>&1 &", getRunDir(), getLogFileLocation())),
                                format("nohup sleep 100000 > %s 2>&1 < /dev/null &", getLogFileLocation()),
                                format("echo $! > "+getPidFile()),
                                format("for i in {1..10}\n" +
                                        "do\n" +
                                        "    test -f %1$s && ps -p `cat %1$s` && exit\n" +
                                        "    sleep 1\n" +
                                        "done\n" +
                                        "echo \"No explicit error launching nginx but couldn't find process by pid; continuing but may subsequently fail\"\n" +
                                        "cat %2$s | tee /dev/stderr",
                                        getPidFile(), getLogFileLocation()))
                        .execute();
            }
        }

        // Use pid file, because just simulating the run of nginx
        @Override
        public void stop() {
            newScript(MutableMap.of("usePidFile", getPidFile()), STOPPING).execute();
        }
    }
}
