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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.TaskAdaptable;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters.StopMode;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.brooklynnode.EntityHttpClient.ResponseCodePredicates;
import brooklyn.entity.brooklynnode.effector.BrooklynNodeUpgradeEffectorBody;
import brooklyn.entity.brooklynnode.effector.SetHighAvailabilityModeEffectorBody;
import brooklyn.entity.brooklynnode.effector.SetHighAvailabilityPriorityEffectorBody;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.basic.Locations;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.javalang.Enums;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;

public class BrooklynNodeImpl extends SoftwareProcessImpl implements BrooklynNode {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeImpl.class);

    static {
        RendererHints.register(WEB_CONSOLE_URI, RendererHints.namedActionWithUrl());
    }

    private static class UnmanageTask implements Runnable {
        private Task<?> latchTask;
        private Entity unmanageEntity;

        public UnmanageTask(@Nullable Task<?> latchTask, Entity unmanageEntity) {
            this.latchTask = latchTask;
            this.unmanageEntity = unmanageEntity;
        }

        public void run() {
            if (latchTask != null) {
                latchTask.blockUntilEnded();
            } else {
                log.debug("No latch task provided for UnmanageTask, falling back to fixed wait");
                Time.sleep(Duration.FIVE_SECONDS);
            }
            synchronized (this) {
                Entities.unmanage(unmanageEntity);
            }
        }
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
        getMutableEntityType().addEffector(SetHighAvailabilityPriorityEffectorBody.SET_HIGH_AVAILABILITY_PRIORITY);
        getMutableEntityType().addEffector(SetHighAvailabilityModeEffectorBody.SET_HIGH_AVAILABILITY_MODE);
        getMutableEntityType().addEffector(BrooklynNodeUpgradeEffectorBody.UPGRADE);
    }

    @Override
    protected void preStart() {
        ServiceNotUpLogic.clearNotUpIndicator(this, SHUTDOWN.getName());
    }

    @Override
    protected void preStopConfirmCustom() {
        super.preStopConfirmCustom();
        ConfigBag stopParameters = BrooklynTaskTags.getCurrentEffectorParameters();
        if (Boolean.TRUE.equals(getAttribute(BrooklynNode.WEB_CONSOLE_ACCESSIBLE)) &&
                stopParameters != null && !stopParameters.containsKey(ShutdownEffector.STOP_APPS_FIRST)) {
            Preconditions.checkState(getChildren().isEmpty(), "Can't stop instance with running applications.");
        }
    }

    @Override
    protected void preStop() {
        super.preStop();
        if (MachineLifecycleEffectorTasks.canStop(getStopProcessModeParam(), this)) {
            shutdownGracefully();
        }
    }

    private StopMode getStopProcessModeParam() {
        ConfigBag parameters = BrooklynTaskTags.getCurrentEffectorParameters();
        if (parameters != null) {
            return parameters.get(StopSoftwareParameters.STOP_PROCESS_MODE);
        } else {
            return StopSoftwareParameters.STOP_PROCESS_MODE.getDefaultValue();
        }
    }

    @Override
    protected void preRestart() {
        super.preRestart();
        //restart will kill the process, try to shut down before that
        shutdownGracefully();
        DynamicTasks.queue("pre-restart", new Runnable() { public void run() {
            //set by shutdown - clear it so the entity starts cleanly. Does the indicator bring any value at all?
            ServiceNotUpLogic.clearNotUpIndicator(BrooklynNodeImpl.this, SHUTDOWN.getName());
        }});
    }

    private void shutdownGracefully() {
        // Shutdown only if accessible: any of stop_* could have already been called.
        // Don't check serviceUp=true because stop() will already have set serviceUp=false && expectedState=stopping
        if (Boolean.TRUE.equals(getAttribute(BrooklynNode.WEB_CONSOLE_ACCESSIBLE))) {
            queueShutdownTask();
            queueWaitExitTask();
        } else {
            log.info("Skipping graceful shutdown call, because web-console not up for {}", this);
        }
    }

    private void queueWaitExitTask() {
        //give time to the process to die gracefully after closing the shutdown call
        DynamicTasks.queue(Tasks.builder().name("wait for graceful stop").body(new Runnable() {
            @Override
            public void run() {
                DynamicTasks.markInessential();
                boolean cleanExit = Repeater.create()
                    .until(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return !getDriver().isRunning();
                        }
                    })
                    .backoffTo(Duration.ONE_SECOND)
                    .limitTimeTo(Duration.ONE_MINUTE)
                    .run();
                if (!cleanExit) {
                    log.warn("Tenant " + this + " didn't stop cleanly after shutdown. Timeout waiting for process exit.");
                }
            }
        }).build());
    }

    @Override
    protected void postStop() {
        super.postStop();
        if (isMachineStopped()) {
            // Don't unmanage in entity's task context as it will self-cancel the task. Wait for the stop effector to complete (and all parent entity tasks).
            // If this is not enough (still getting Caused by: java.util.concurrent.CancellationException: null) then
            // we could wait for BrooklynTaskTags.getTasksInEntityContext(ExecutionManager, this).isEmpty();
            Task<?> stopEffectorTask = BrooklynTaskTags.getClosestEffectorTask(Tasks.current(), Startable.STOP);
            Task<?> topEntityTask = getTopEntityTask(stopEffectorTask);
            getManagementContext().getExecutionManager().submit(new UnmanageTask(topEntityTask, this));
        }
    }

    private Task<?> getTopEntityTask(Task<?> stopEffectorTask) {
        Entity context = BrooklynTaskTags.getContextEntity(stopEffectorTask);
        Task<?> topTask = stopEffectorTask;
        while (true) {
            Task<?> parentTask = topTask.getSubmittedByTask();
            Entity parentContext = BrooklynTaskTags.getContextEntity(parentTask);
            if (parentTask == null || parentContext != context) {
                return topTask;
            } else {
                topTask = parentTask;
            }
        }
    }

    private boolean isMachineStopped() {
        // Don't rely on effector parameters, check if there is still a machine running.
        // If the entity was previously stopped with STOP_MACHINE_MODE=StopMode.NEVER
        // and a second time with STOP_MACHINE_MODE=StopMode.IF_NOT_STOPPED, then the
        // machine is still running, but there is no deterministic way to infer this from
        // the parameters alone.
        return Locations.findUniqueSshMachineLocation(this.getLocations()).isAbsent();
    }

    private void queueShutdownTask() {
        ConfigBag stopParameters = BrooklynTaskTags.getCurrentEffectorParameters();
        ConfigBag shutdownParameters;
        if (stopParameters != null) {
            shutdownParameters = ConfigBag.newInstanceCopying(stopParameters);
        } else {
            shutdownParameters = ConfigBag.newInstance();
        }
        shutdownParameters.putIfAbsent(ShutdownEffector.REQUEST_TIMEOUT, Duration.ONE_MINUTE);
        shutdownParameters.putIfAbsent(ShutdownEffector.FORCE_SHUTDOWN_ON_ERROR, Boolean.TRUE);
        TaskAdaptable<Void> shutdownTask = Effectors.invocation(this, SHUTDOWN, shutdownParameters);
        //Mark inessential so that even if it fails the process stop task will run afterwards to clean up.
        TaskTags.markInessential(shutdownTask);
        DynamicTasks.queue(shutdownTask);
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
        public String submitPlan(final String plan) {
            final MutableMap<String, String> headers = MutableMap.of(com.google.common.net.HttpHeaders.CONTENT_TYPE, "application/yaml");
            final AtomicReference<byte[]> response = new AtomicReference<byte[]>();
            Repeater.create()
                .every(Duration.ONE_SECOND)
                .backoffTo(Duration.FIVE_SECONDS)
                .limitTimeTo(Duration.minutes(5))
                .repeat(Runnables.doNothing())
                .rethrowExceptionImmediately()
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        HttpToolResponse result = ((BrooklynNode)entity()).http()
                                //will throw on non-{2xx, 403} response
                                .responseSuccess(Predicates.<Integer>or(ResponseCodePredicates.success(), Predicates.equalTo(HttpStatus.SC_FORBIDDEN)))
                                .post("/v1/applications", headers, plan.getBytes());
                        if (result.getResponseCode() == HttpStatus.SC_FORBIDDEN) {
                            log.debug("Remote is not ready to accept requests, response is " + result.getResponseCode());
                            return false;
                        } else {
                            byte[] content = result.getContent();
                            response.set(content);
                            return true;
                        }
                    }
                })
                .runRequiringTrue();
            return (String)new Gson().fromJson(new String(response.get()), Map.class).get("entityId");
        }
    }

    public static class ShutdownEffectorBody extends EffectorBody<Void> implements ShutdownEffector {
        public static final Effector<Void> SHUTDOWN = Effectors.effector(BrooklynNode.SHUTDOWN).impl(new ShutdownEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            MutableMap<String, String> formParams = MutableMap.of();
            Lifecycle initialState = entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
            for (ConfigKey<?> k: new ConfigKey<?>[] { STOP_APPS_FIRST, FORCE_SHUTDOWN_ON_ERROR, SHUTDOWN_TIMEOUT, REQUEST_TIMEOUT, DELAY_FOR_HTTP_RETURN })
                formParams.addIfNotNull(k.getName(), toNullableString(parameters.get(k)));
            try {
                log.debug("Shutting down "+entity()+" with "+formParams);
                HttpToolResponse resp = ((BrooklynNode)entity()).http()
                    .post("/v1/server/shutdown",
                        ImmutableMap.of("Brooklyn-Allow-Non-Master-Access", "true"),
                        formParams);
                if (resp.getResponseCode() != HttpStatus.SC_NO_CONTENT) {
                    throw new IllegalStateException("Response code "+resp.getResponseCode());
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new PropagatedRuntimeException("Error shutting down remote node "+entity()+" (in state "+initialState+"): "+Exceptions.collapseText(e), e);
            }
            ServiceNotUpLogic.updateNotUpIndicator(entity(), SHUTDOWN.getName(), "Shutdown of remote node has completed successfuly");
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

            ConfigBag stopParameters = ConfigBag.newInstanceCopying(parameters);
            stopParameters.put(ShutdownEffector.STOP_APPS_FIRST, Boolean.FALSE);
            stopParameters.putIfAbsent(ShutdownEffector.SHUTDOWN_TIMEOUT, timeout);
            stopParameters.putIfAbsent(ShutdownEffector.REQUEST_TIMEOUT, timeout);
            DynamicTasks.queue(Effectors.invocation(entity(), STOP, stopParameters)).asTask().getUnchecked();
            return null;
        }
    }

    public static class StopNodeAndKillAppsEffectorBody extends EffectorBody<Void> implements StopNodeAndKillAppsEffector {
        public static final Effector<Void> STOP_NODE_AND_KILL_APPS = Effectors.effector(BrooklynNode.STOP_NODE_AND_KILL_APPS).impl(new StopNodeAndKillAppsEffectorBody()).build();

        @Override
        public Void call(ConfigBag parameters) {
            Duration timeout = parameters.get(TIMEOUT);

            ConfigBag stopParameters = ConfigBag.newInstanceCopying(parameters);
            stopParameters.put(ShutdownEffector.STOP_APPS_FIRST, Boolean.TRUE);
            stopParameters.putIfAbsent(ShutdownEffector.SHUTDOWN_TIMEOUT, timeout);
            stopParameters.putIfAbsent(ShutdownEffector.REQUEST_TIMEOUT, timeout);
            DynamicTasks.queue(Effectors.invocation(entity(), STOP, stopParameters)).asTask().getUnchecked();
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

        URI webConsoleUri;
        if (isHttpProtocolEnabled("http")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTP_PORT));
            HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, port);
            webConsoleUri = URI.create(String.format("http://%s:%s", accessible.getHostText(), accessible.getPort()));
        } else if (isHttpProtocolEnabled("https")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTPS_PORT));
            HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, port);
            webConsoleUri = URI.create(String.format("https://%s:%s", accessible.getHostText(), accessible.getPort()));
        } else {
            // web-console is not enabled
            webConsoleUri = null;
        }
        setAttribute(WEB_CONSOLE_URI, webConsoleUri);

        if (webConsoleUri != null) {
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(getConfig(POLL_PERIOD))
                    .baseUri(webConsoleUri)
                    .credentialsIfNotNull(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Boolean>(WEB_CONSOLE_ACCESSIBLE)
                            .suburl("/v1/server/healthy")
                            .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.cast(Boolean.class)))
                            //if using an old distribution the path doesn't exist, but at least the instance is responding
                            .onFailure(HttpValueFunctions.responseCodeEquals(404))
                            .setOnException(false))
                    .poll(new HttpPollConfig<ManagementNodeState>(MANAGEMENT_NODE_STATE)
                            .suburl("/v1/server/ha/state")
                            .onSuccess(Functionals.chain(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.cast(String.class)), Enums.fromStringFunction(ManagementNodeState.class)))
                            .setOnFailureOrException(null))
                    // TODO sensors for load, size, etc
                    .build();

            if (!Lifecycle.RUNNING.equals(getAttribute(SERVICE_STATE_ACTUAL))) {
                // TODO when updating the map, if it would change from empty to empty on a successful run (see in nginx)
                ServiceNotUpLogic.updateNotUpIndicator(this, WEB_CONSOLE_ACCESSIBLE, "No response from the web console yet");
            }
            addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                .from(WEB_CONSOLE_ACCESSIBLE)
                .computing(Functionals.ifNotEquals(true).value("URL where Brooklyn listens is not answering correctly") )
                .build());
        } else {
            connectServiceUpIsRunning();
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
