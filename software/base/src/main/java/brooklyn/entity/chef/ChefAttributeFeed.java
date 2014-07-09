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
package brooklyn.entity.chef;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A sensor feed that retrieves attributes from Chef server and converts selected attributes to sensors.
 *
 * <p>To use this feed, you must provide the entity, the name of the node as it is known to Chef, and a collection of attribute
 * sensors. The attribute sensors must follow the naming convention of starting with the string <tt>chef.attribute.</tt>
 * followed by a period-separated path through the Chef attribute hierarchy. For example, an attribute sensor named
 * <tt>chef.attribute.sql_server.instance_name</tt> would cause the feed to search for a Chef attribute called
 * <tt>sql_server</tt>, and within that an attribute <tt>instance_name</tt>, and set the sensor to the value of this
 * attribute.</p>
 *
 * <p>This feed uses the <tt>knife</tt> tool to query all the attributes on a named node. It then iterates over the configured
 * list of attribute sensors, using the sensor name to locate an equivalent Chef attribute. The sensor is then set to the value
 * of the Chef attribute.</p>
 *
 * <p>Example:</p>
 *
 * {@code
 * @Override
 * protected void connectSensors() {
 *     nodeAttributesFeed = ChefAttributeFeed.newFeed(this, nodeName, new AttributeSensor[]{
 *             SqlServerNode.CHEF_ATTRIBUTE_NODE_NAME,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_INSTANCE_NAME,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_PORT,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_SA_PASSWORD
 *     });
 * }
 * }
 *
 * @since 0.6.0
 * @author richardcloudsoft
 */
public class ChefAttributeFeed extends AbstractFeed {

    /**
     * Prefix for attribute sensor names.
     */
    public static final String CHEF_ATTRIBUTE_PREFIX = "chef.attribute.";

    private static final Logger log = LoggerFactory.getLogger(ChefAttributeFeed.class);

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("rawtypes")
    public static class Builder {
        private EntityLocal entity;
        private boolean onlyIfServiceUp = false;
        private String nodeName;
        private Map<String, AttributeSensor<?>> sensors = Maps.newHashMap();
        private long period = 30;
        private TimeUnit periodUnits = TimeUnit.SECONDS;
        private volatile boolean built;

        public Builder entity(EntityLocal val) {
            this.entity = checkNotNull(val, "entity");
            return this;
        }
        public Builder onlyIfServiceUp() { return onlyIfServiceUp(true); }
        public Builder onlyIfServiceUp(boolean onlyIfServiceUp) { 
            this.onlyIfServiceUp = onlyIfServiceUp; 
            return this; 
        }
        public Builder nodeName(String nodeName) {
            this.nodeName = checkNotNull(nodeName, "nodeName");
            return this;
        }
        public Builder addSensor(String chefAttributePath, AttributeSensor sensor) {
            sensors.put(checkNotNull(chefAttributePath, "chefAttributePath"), checkNotNull(sensor, "sensor"));
            return this;
        }
        public Builder addSensors(Map<String, AttributeSensor> sensors) {
            sensors.putAll(checkNotNull(sensors, "sensors"));
            return this;
        }
        public Builder addSensors(AttributeSensor[] sensors) {
            return addSensors(Arrays.asList(checkNotNull(sensors, "sensors")));
        }
        public Builder addSensors(Iterable<AttributeSensor> sensors) {
            for(AttributeSensor sensor : checkNotNull(sensors, "sensors")) {
                checkNotNull(sensor, "sensors collection contains a null value");
                checkArgument(sensor.getName().startsWith(CHEF_ATTRIBUTE_PREFIX), "sensor name must be prefixed "+CHEF_ATTRIBUTE_PREFIX+" for autodetection to work");
                addSensor(sensor.getName().substring(CHEF_ATTRIBUTE_PREFIX.length()), sensor);
            }
            return this;
        }
        public Builder period(Duration period) {
            return period(checkNotNull(period, "period").toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = checkNotNull(units, "units");
            return this;
        }
        public ChefAttributeFeed build() {
            built = true;
            ChefAttributeFeed result = new ChefAttributeFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("SshFeed.Builder created, but build() never called");
        }
    }

    private final EntityLocal entity;
    private final String nodeName;
    private final long period;
    private final TimeUnit periodUnits;
    private final Map<String, AttributeSensor<?>> chefAttributeSensors;
    private final KnifeTaskFactory<String> knifeTaskFactory;

    protected ChefAttributeFeed(Builder builder) {
        super(checkNotNull(builder.entity, "builder.entity"), builder.onlyIfServiceUp);
        entity = builder.entity;
        nodeName = checkNotNull(builder.nodeName, "builder.nodeName");
        period = builder.period;
        periodUnits = builder.periodUnits;
        chefAttributeSensors = builder.sensors;
        knifeTaskFactory = new KnifeNodeAttributeQueryTaskFactory(nodeName);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void preStart() {
        final Callable<SshPollValue> getAttributesFromKnife = new Callable<SshPollValue>() {
            public SshPollValue call() throws Exception {
                ProcessTaskWrapper<String> taskWrapper = knifeTaskFactory.newTask();
                final ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
                log.debug("START: Running knife to query attributes of Chef node {}", nodeName);
                executionContext.submit(taskWrapper);
                taskWrapper.block();
                log.debug("DONE:  Running knife to query attributes of Chef node {}", nodeName);
                return new SshPollValue(null, taskWrapper.getExitCode(), taskWrapper.getStdout(), taskWrapper.getStderr());
            }
        };

        ((Poller<SshPollValue>) poller).scheduleAtFixedRate(
                new CallInEntityExecutionContext<SshPollValue>(entity, getAttributesFromKnife),
                new SendChefAttributesToSensors(entity, chefAttributeSensors),
                periodUnits.toMillis(period));
    }

    /**
     * An implementation of {@link KnifeTaskFactory} that queries for the attributes of a node.
     */
    private static class KnifeNodeAttributeQueryTaskFactory extends KnifeTaskFactory<String> {
        private final String nodeName;

        public KnifeNodeAttributeQueryTaskFactory(String nodeName) {
            super("retrieve attributes of node " + nodeName);
            this.nodeName = nodeName;
        }

        @Override
        protected List<String> initialKnifeParameters() {
            return ImmutableList.of("node", "show", "-l", nodeName, "--format", "json");
        }
    }

    /**
     * A {@link Callable} that wraps another {@link Callable}, where the inner {@link Callable} is executed in the context of a
     * specific entity.
     *
     * @param <T> The type of the {@link Callable}.
     */
    private static class CallInEntityExecutionContext<T> implements Callable<T> {

        private final Callable<T> job;
        private EntityLocal entity;

        private CallInEntityExecutionContext(EntityLocal entity, Callable<T> job) {
            this.job = job;
            this.entity = entity;
        }

        @Override
        public T call() throws Exception {
            final ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
            return executionContext.submit(Maps.newHashMap(), job).get();
        }
    }

    /**
     * A poll handler that takes the result of the <tt>knife</tt> invocation and sets the appropriate sensors.
     */
    private static class SendChefAttributesToSensors implements PollHandler<SshPollValue> {
        private static final Iterable<String> PREFIXES = ImmutableList.of("", "automatic", "force_override", "override", "normal", "force_default", "default");
        private static final Splitter SPLITTER = Splitter.on('.');

        private Map<String, AttributeSensor<?>> chefAttributeSensors;
        private EntityLocal entity;

        public SendChefAttributesToSensors(EntityLocal entity, Map<String, AttributeSensor<?>> chefAttributeSensors) {
            this.chefAttributeSensors = chefAttributeSensors;
            this.entity = entity;
        }

        @Override
        public boolean checkSuccess(SshPollValue val) {
            if (val.getExitStatus() != 0) return false;
            String stderr = val.getStderr();
            if (stderr == null || stderr.length() != 0) return false;
            String out = val.getStdout();
            if (out == null || out.length() == 0) return false;
            if (!out.contains("{")) return false;
            return true;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public void onSuccess(SshPollValue val) {
            String stdout = val.getStdout();
            int jsonStarts = stdout.indexOf('{');
            if (jsonStarts > 0)
                stdout = stdout.substring(jsonStarts);
            JsonElement jsonElement = new Gson().fromJson(stdout, JsonElement.class);

            for (Map.Entry<String, AttributeSensor<?>> attribute : chefAttributeSensors.entrySet()) {
                String chefAttributeName = attribute.getKey();
                AttributeSensor<?> sensor = attribute.getValue();
                log.trace("Finding value for attribute sensor " + sensor.getName());

                Iterable<String> path = SPLITTER.split(chefAttributeName);
                JsonElement elementForSensor = null;
                for(String prefix : PREFIXES) {
                    Iterable<String> prefixedPath = !Strings.isNullOrEmpty(prefix)
                            ? Iterables.concat(ImmutableList.of(prefix), path)
                            : path;
                    try {
                        elementForSensor = getElementByPath(jsonElement.getAsJsonObject(), prefixedPath);
                    } catch(IllegalArgumentException e) {
                        log.error("Entity {}: bad Chef attribute {} for sensor {}: {}", new Object[]{
                                entity.getDisplayName(),
                                Joiner.on('.').join(prefixedPath),
                                sensor.getName(),
                                e.getMessage()});
                        throw Throwables.propagate(e);
                    }
                    if (elementForSensor != null) {
                        log.debug("Entity {}: apply Chef attribute {} to sensor {} with value {}", new Object[]{
                                entity.getDisplayName(),
                                Joiner.on('.').join(prefixedPath),
                                sensor.getName(),
                                elementForSensor.getAsString()});
                        break;
                    }
                }
                if (elementForSensor != null) {
                    entity.setAttribute((AttributeSensor)sensor, TypeCoercions.coerce(elementForSensor.getAsString(), sensor.getType()));
                } else {
                    log.debug("Entity {}: no Chef attribute matching {}; setting sensor {} to null", new Object[]{
                            entity.getDisplayName(),
                            chefAttributeName,
                            sensor.getName()});
                    entity.setAttribute(sensor, null);
                }
            }
        }

        private JsonElement getElementByPath(JsonElement element, Iterable<String> path) {
            if (Iterables.isEmpty(path)) {
                return element;
            } else {
                String head = Iterables.getFirst(path, null);
                Preconditions.checkArgument(!Strings.isNullOrEmpty(head), "path must not contain empty or null elements");
                Iterable<String> tail = Iterables.skip(path, 1);
                JsonElement child = ((JsonObject) element).get(head);
                return child != null
                        ? getElementByPath(child, tail)
                        : null;
            }
        }

        @Override
        public void onFailure(SshPollValue val) {
            log.error("Chef attribute query did not respond as expected. exitcode={} stdout={} stderr={}", new Object[]{val.getExitStatus(), val.getStdout(), val.getStderr()});
            for (AttributeSensor<?> attribute : chefAttributeSensors.values()) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;
                entity.setAttribute(attribute, null);
            }
        }

        @Override
        public void onException(Exception exception) {
            log.error("Detected exception while retrieving Chef attributes from entity " + entity.getDisplayName(), exception);
            for (AttributeSensor<?> attribute : chefAttributeSensors.values()) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;
                entity.setAttribute(attribute, null);
            }
        }
        
        @Override
        public String toString() {
            return super.toString()+"["+getDescription()+"]";
        }
        
        @Override
        public String getDescription() {
            return ""+chefAttributeSensors;
        }
    }
    
}
