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
package brooklyn.entity.brooklynnode;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.brooklynnode.effector.SetHAModeEffectorBody;
import brooklyn.entity.brooklynnode.effector.SetHAPriorityEffectorBody;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.javalang.Enums;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

public class BrooklynNodeImpl extends SoftwareProcessImpl implements BrooklynNode {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeImpl.class);

    static {
        RendererHints.register(WEB_CONSOLE_URI, RendererHints.namedActionWithUrl());
    }

    private HttpFeed httpFeed;
    
    public BrooklynNodeImpl() {
        super();
    }

    public BrooklynNodeImpl(Entity parent) {
        super(parent);
    }
    
    @Override
    public Class<?> getDriverInterface() {
        return BrooklynNodeDriver.class;
    }

    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(DeployBlueprintEffectorBody.DEPLOY_BLUEPRINT);
        getMutableEntityType().addEffector(ShutdownEffectorBody.SHUTDOWN);
        getMutableEntityType().addEffector(StopNodeButLeaveAppsEffectorBody.STOP_NODE_BUT_LEAVE_APPS);
        getMutableEntityType().addEffector(StopNodeAndKillAppsEffectorBody.STOP_NODE_AND_KILL_APPS);
        getMutableEntityType().addEffector(SetHAPriorityEffectorBody.SET_HA_PRIORITY);
        getMutableEntityType().addEffector(SetHAModeEffectorBody.SET_HA_MODE);
    }

    @Override
    protected void doStop() {
        //shutdown only if running, any of stop_* could've been already previously
        if (getAttribute(Attributes.SERVICE_UP)) {
            Preconditions.checkState(getChildren().isEmpty(), "Can't stop instance with running applications.");
            DynamicTasks.queue(Effectors.invocation(this, SHUTDOWN, MutableMap.of(ShutdownEffector.REQUEST_TIMEOUT, Duration.ONE_MINUTE)));
        }
        super.doStop();
    }

    public static class DeployBlueprintEffectorBody extends EffectorBody<String> implements DeployBlueprintEffector {
        public static final Effector<String> DEPLOY_BLUEPRINT = Effectors.effector(BrooklynNode.DEPLOY_BLUEPRINT).impl(new DeployBlueprintEffectorBody()).build();
        
        // TODO support YAML parsing
        // TODO define a new type YamlMap for the config key which supports coercing from string and from map
        @SuppressWarnings("unchecked")
        public static Map<String,Object> asMap(ConfigBag parameters, ConfigKey<?> key) {
            Object v = parameters.getStringKey(key.getName());
            if (v==null || (v instanceof String && Strings.isBlank((String)v)))
                return null;
            if (v instanceof Map) 
                return (Map<String, Object>) v;
            
            if (v instanceof String) {
                // TODO ideally, parse YAML 
                return new Gson().fromJson((String)v, Map.class);
            }
            throw new IllegalArgumentException("Invalid "+JavaClassNames.simpleClassName(v)+" value for "+key+": "+v);
        }
        
        @Override
        public String call(ConfigBag parameters) {
            if (log.isDebugEnabled())
                log.debug("Deploying blueprint to "+entity()+": "+parameters);
            String plan = extractPlanYamlString(parameters);
            return submitPlan(plan);
        }

        protected String extractPlanYamlString(ConfigBag parameters) {
            Object planRaw = parameters.getStringKey(BLUEPRINT_CAMP_PLAN.getName());
            if (planRaw instanceof String && Strings.isBlank((String)planRaw)) planRaw = null;
            
            String url = parameters.get(BLUEPRINT_TYPE);
            if (url!=null && planRaw!=null)
                throw new IllegalArgumentException("Cannot supply both plan and url");
            if (url==null && planRaw==null)
                throw new IllegalArgumentException("Must supply plan or url");
            
            Map<String, Object> config = asMap(parameters, BLUEPRINT_CONFIG);
            
            if (planRaw==null) {
                planRaw = Jsonya.at("services").list().put("serviceType", url).putIfNotNull("brooklyn.config", config).getRootMap();
            } else { 
                if (config!=null)
                    throw new IllegalArgumentException("Cannot supply plan with config");
            }
            
            // planRaw might be a yaml string, or a map; if a map, convert to string
            if (planRaw instanceof Map)
                planRaw = Jsonya.of((Map<?,?>)planRaw).toString();
            if (!(planRaw instanceof String))
                throw new IllegalArgumentException("Invalid "+JavaClassNames.simpleClassName(planRaw)+" value for CAMP plan: "+planRaw);
            
            // now *all* the data is in planRaw; that is what will be submitted
            return (String)planRaw;
        }
        
        @VisibleForTesting
        // Integration test for this in BrooklynNodeIntegrationTest in this project doesn't use this method,
        // but a Unit test for this does, in DeployBlueprintTest -- but in the REST server project (since it runs against local) 
        public String submitPlan(String plan) {
            MutableMap<String, String> headers = MutableMap.of(com.google.common.net.HttpHeaders.CONTENT_TYPE, "application/yaml");
            HttpToolResponse result = ((BrooklynNode)entity()).http()
                    .post("/v1/applications", headers, plan.getBytes());
            byte[] content = result.getContent();
            return (String)new Gson().fromJson(new String(content), Map.class).get("entityId");
        }
    }

    public static class ShutdownEffectorBody extends EffectorBody<Void> implements ShutdownEffector {
        public static final Effector<Void> SHUTDOWN = Effectors.effector(BrooklynNode.SHUTDOWN).impl(new ShutdownEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            Map<String, String> formParams = new MutableMap<String, String>()
                    .addIfNotNull("stopAppsFirst", toNullableString(parameters.get(STOP_APPS_FIRST)))
                    .addIfNotNull("forceShutdownOnError", toNullableString(parameters.get(FORCE_SHUTDOWN_ON_ERROR)))
                    .addIfNotNull("shutdownTimeout", toNullableString(parameters.get(SHUTDOWN_TIMEOUT)))
                    .addIfNotNull("requestTimeout", toNullableString(parameters.get(REQUEST_TIMEOUT)))
                    .addIfNotNull("delayForHttpReturn", toNullableString(parameters.get(DELAY_FOR_HTTP_RETURN)));
            try {
                HttpToolResponse resp = ((BrooklynNode)entity()).http()
                    .post("/v1/server/shutdown",
                        ImmutableMap.of("Brooklyn-Allow-Non-Master-Access", "true"),
                        formParams);
                if (resp.getResponseCode() != HttpStatus.SC_NO_CONTENT) {
                    throw new IllegalStateException("Response code "+resp.getResponseCode());
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                Lifecycle state = entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL);
                if (state!=Lifecycle.RUNNING) {
                    // ignore failure in this task if the node is not currently running
                    Tasks.markInessential();
                }
                throw new PropagatedRuntimeException("Error shutting down remote node "+entity()+" (in state "+state+"): "+Exceptions.collapseText(e), e);
            }
            ServiceNotUpLogic.updateNotUpIndicator(entity(), "brooklynnode.shutdown", "Shutdown of remote node has completed successfuly");
            return null;
        }

        private static String toNullableString(Object obj) {
            if (obj == null) {
                return null;
            } else {
                return obj.toString();
            }
        }

    }

    public static class StopNodeButLeaveAppsEffectorBody extends EffectorBody<Void> implements StopNodeButLeaveAppsEffector {
        public static final Effector<Void> STOP_NODE_BUT_LEAVE_APPS = Effectors.effector(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS).impl(new StopNodeButLeaveAppsEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            Duration timeout = parameters.get(TIMEOUT);
            MutableMap<?, ?> params = MutableMap.of(
                    ShutdownEffector.STOP_APPS_FIRST, Boolean.FALSE,
                    ShutdownEffector.SHUTDOWN_TIMEOUT, timeout,
                    ShutdownEffector.REQUEST_TIMEOUT, timeout);
            Entity entity = entity();
            TaskAdaptable<Void> shutdownTask = Effectors.invocation(entity, SHUTDOWN, params);
            if (!entity.getAttribute(SERVICE_UP)) {
                TaskTags.markInessential(shutdownTask);
            }
            DynamicTasks.queue(shutdownTask).asTask().getUnchecked();
            Entities.destroy(entity);
            return null;
        }
    }

    public static class StopNodeAndKillAppsEffectorBody extends EffectorBody<Void> implements StopNodeAndKillAppsEffector {
        public static final Effector<Void> STOP_NODE_AND_KILL_APPS = Effectors.effector(BrooklynNode.STOP_NODE_AND_KILL_APPS).impl(new StopNodeAndKillAppsEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            Duration timeout = parameters.get(TIMEOUT);
            MutableMap<?, ?> params = MutableMap.of(
                    ShutdownEffector.STOP_APPS_FIRST, Boolean.TRUE,
                    ShutdownEffector.SHUTDOWN_TIMEOUT, timeout,
                    ShutdownEffector.REQUEST_TIMEOUT, timeout);
            Entity entity = entity();
            TaskAdaptable<Void> shutdownTask = Effectors.invocation(entity, SHUTDOWN, params);
            if (!entity.getAttribute(SERVICE_UP)) {
                TaskTags.markInessential(shutdownTask);
            }
            DynamicTasks.queue(shutdownTask).asTask().getUnchecked();
            Entities.destroy(entity);
            return null;
        }
    }

    public List<String> getClasspath() {
        List<String> classpath = getConfig(CLASSPATH);
        if (classpath == null || classpath.isEmpty()) {
            classpath = getManagementContext().getConfig().getConfig(CLASSPATH);
        }
        return classpath;
    }
    
    protected List<String> getEnabledHttpProtocols() {
        return getAttribute(ENABLED_HTTP_PROTOCOLS);
    }
    
    protected boolean isHttpProtocolEnabled(String protocol) {
        List<String> protocols = getAttribute(ENABLED_HTTP_PROTOCOLS);
        for (String contender : protocols) {
            if (protocol.equalsIgnoreCase(contender)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        // TODO what sensors should we poll?
        ConfigToAttributes.apply(this);

        InetAddress address = getAttribute(WEB_CONSOLE_PUBLIC_ADDRESS);
        String host;
        if (address == null) {
            if (getAttribute(NO_WEB_CONSOLE_AUTHENTICATION)) {
                host = "localhost"; // Because of --noConsoleSecurity option
            } else {
                host = getAttribute(HOSTNAME);
            }
        } else {
            host = address.getHostName();
        }

        URI webConsoleUri;
        if (isHttpProtocolEnabled("http")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTP_PORT));
            webConsoleUri = URI.create(String.format("http://%s:%s", host, port));
            setAttribute(WEB_CONSOLE_URI, webConsoleUri);
        } else if (isHttpProtocolEnabled("https")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTPS_PORT));
            webConsoleUri = URI.create(String.format("https://%s:%s", host, port));
            setAttribute(WEB_CONSOLE_URI, webConsoleUri);
        } else {
            // web-console is not enabled
            setAttribute(WEB_CONSOLE_URI, null);
            webConsoleUri = null;
        }

        connectServiceUpIsRunning();

        if (webConsoleUri != null) {
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri(webConsoleUri)
                    .credentialsIfNotNull(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Boolean>(WEB_CONSOLE_ACCESSIBLE)
                            .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                            .setOnFailureOrException(false))
                    .poll(new HttpPollConfig<ManagementNodeState>(MANAGEMENT_NODE_STATE)
                            .suburl("/v1/server/ha/state")
                            .onSuccess(Functionals.chain(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.cast(String.class)), Enums.fromStringFunction(ManagementNodeState.class)))
                            .setOnFailureOrException(null))
                    .build();

            if (!Lifecycle.RUNNING.equals(getAttribute(SERVICE_STATE_ACTUAL))) {
                // TODO when updating the map, if it would change from empty to empty on a successful run (see in nginx)
                ServiceNotUpLogic.updateNotUpIndicator(this, WEB_CONSOLE_ACCESSIBLE, "No response from the web console yet");
            }
            addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                .from(WEB_CONSOLE_ACCESSIBLE)
                .computing(Functionals.ifNotEquals(true).value("URL where Brooklyn listens is not answering correctly") )
                .build());
        }
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (httpFeed != null) httpFeed.stop();
    }

    @Override
    public EntityHttpClient http() {
        return new EntityHttpClientImpl(this, BrooklynNode.WEB_CONSOLE_URI);
    }

}
