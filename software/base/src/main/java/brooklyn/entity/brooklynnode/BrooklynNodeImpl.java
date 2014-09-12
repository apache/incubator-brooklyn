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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;

public class BrooklynNodeImpl extends SoftwareProcessImpl implements BrooklynNode {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeImpl.class);

    static {
        RendererHints.register(WEB_CONSOLE_URI, new RendererHints.NamedActionWithUrl("Open"));
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
    }

    @Override
    protected void doStop() {
        Preconditions.checkState(getChildren().isEmpty(), "Can't stop instance with running applications.");
        DynamicTasks.queue(Effectors.invocation(this, SHUTDOWN, MutableMap.of(ShutdownEffector.HTTP_RETURN_TIMEOUT, Duration.ONE_MINUTE)));
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
            Map<String, String> formParams = MutableMap.<String, String>of(
                    "stopAppsFirst", parameters.get(STOP_APPS).toString(),
                    "delayMillis", getDelayMillis(parameters));
            Duration httpReturnTimeout = parameters.get(HTTP_RETURN_TIMEOUT);
            if (httpReturnTimeout != null) {
                formParams.put("httpReturnTimeout", httpReturnTimeout.toString());
            }
            HttpToolResponse resp = ((BrooklynNode)entity()).http()
                .post("/v1/server/shutdown", MutableMap.<String, String>of(), formParams);
            if (resp.getResponseCode() != HttpStatus.SC_NO_CONTENT) {
                throw new IllegalStateException("Remote node shutdown failed.");
            }
            return null;
        }

        private String getDelayMillis(ConfigBag parameters) {
            return Long.toString(parameters.get(DELAY).toMilliseconds());
        }

    }

    public static class StopNodeButLeaveAppsEffectorBody extends EffectorBody<Void> implements StopNodeButLeaveAppsEffector {
        public static final Effector<Void> STOP_NODE_BUT_LEAVE_APPS = Effectors.effector(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS).impl(new StopNodeButLeaveAppsEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            MutableMap<?, ?> params = MutableMap.of(
                    ShutdownEffector.STOP_APPS, Boolean.FALSE,
                    ShutdownEffector.HTTP_RETURN_TIMEOUT, Duration.ONE_HOUR);
            Entity entity = entity();
            DynamicTasks.queue(Effectors.invocation(entity, SHUTDOWN, params)).asTask().getUnchecked();
            Entities.unmanage(entity);
            return null;
        }
    }

    public static class StopNodeAndKillAppsEffectorBody extends EffectorBody<Void> implements StopNodeAndKillAppsEffector {
        public static final Effector<Void> STOP_NODE_AND_KILL_APPS = Effectors.effector(BrooklynNode.STOP_NODE_AND_KILL_APPS).impl(new StopNodeAndKillAppsEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            MutableMap<?, ?> params = MutableMap.of(
                    ShutdownEffector.STOP_APPS, Boolean.TRUE,
                    ShutdownEffector.HTTP_RETURN_TIMEOUT, Duration.ONE_HOUR);
            Entity entity = entity();
            DynamicTasks.queue(Effectors.invocation(entity, SHUTDOWN, params)).asTask().getUnchecked();
            Entities.unmanage(entity);
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

        String host = getAttribute(WEB_CONSOLE_BIND_ADDRESS);
        if (Strings.isEmpty(host)) {
            if (getAttribute(NO_WEB_CONSOLE_AUTHENTICATION)) {
                host = "localhost"; // Because of --noConsoleSecurity option
            } else {
                host = getAttribute(HOSTNAME);
            }
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

        if (webConsoleUri != null) {
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri(webConsoleUri)
                    .credentialsIfNotNull(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                            .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                            .setOnFailureOrException(false))
                    .build();

        } else {
            setAttribute(SERVICE_UP, true);
        }
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
    }

    @Override
    public EntityHttpClient http() {
        return new EntityHttpClientImpl(this);
    }

}
