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
package brooklyn.event.feed.windows;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

/**
 * A sensor feed that retrieves performance counters from a Windows host and posts the values to sensors.
 *
 * <p>To use this feed, you must provide the entity, and a collection of mappings between Windows performance counter
 * names and Brooklyn attribute sensors.</p>
 *
 * <p>This feed uses SSH to invoke the windows utility <tt>typeperf</tt> to query for a specific set of performance
 * counters, by name. The values are extracted from the response, and published to the entity's sensors.</p>
 *
 * <p>Example:</p>
 *
 * {@code
 * @Override
 * protected void connectSensors() {
 *     WindowsPerformanceCounterFeed feed = WindowsPerformanceCounterFeed.builder()
 *         .entity(entity)
 *         .addSensor("\\Processor(_total)\\% Idle Time", CPU_IDLE_TIME)
 *         .addSensor("\\Memory\\Available MBytes", AVAILABLE_MEMORY)
 *         .build();
 * }
 * }
 *
 * @since 0.6.0
 * @author richardcloudsoft
 */
public class WindowsPerformanceCounterFeed extends AbstractFeed {

    private static final Logger log = LoggerFactory.getLogger(WindowsPerformanceCounterFeed.class);

    // This pattern matches CSV line(s) with the date in the first field, and at least one further field.
    protected static final Pattern lineWithPerfData = Pattern.compile("^\"[\\d:/\\-. ]+\",\".*\"$", Pattern.MULTILINE);
    private static final Joiner JOINER_ON_SPACE = Joiner.on(' ');
    private static final Joiner JOINER_ON_COMMA = Joiner.on(',');

    @SuppressWarnings("serial")
    public static final ConfigKey<List<WindowsPerformanceCounterPollConfig<?>>> POLLS = ConfigKeys.newConfigKey(
            new TypeToken<List<WindowsPerformanceCounterPollConfig<?>>>() {},
            "polls");

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityLocal entity;
        private Set<WindowsPerformanceCounterPollConfig<?>> polls = Sets.newLinkedHashSet();
        private Duration period = Duration.of(30, TimeUnit.SECONDS);
        private String uniqueTag;
        private volatile boolean built;

        public Builder entity(EntityLocal val) {
            this.entity = checkNotNull(val, "entity");
            return this;
        }
        public Builder addSensor(WindowsPerformanceCounterPollConfig<?> config) {
            polls.add(config);
            return this;
        }
        public Builder addSensor(String performanceCounterName, AttributeSensor<?> sensor) {
            return addSensor(new WindowsPerformanceCounterPollConfig(sensor).performanceCounterName(checkNotNull(performanceCounterName, "performanceCounterName")));
        }
        public Builder addSensors(Map<String, AttributeSensor> sensors) {
            for (Map.Entry<String, AttributeSensor> entry : sensors.entrySet()) {
                addSensor(entry.getKey(), entry.getValue());
            }
            return this;
        }
        public Builder period(Duration period) {
            this.period = checkNotNull(period, "period");
            return this;
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            return period(Duration.of(val, units));
        }
        public Builder uniqueTag(String uniqueTag) {
            this.uniqueTag = uniqueTag;
            return this;
        }
        public WindowsPerformanceCounterFeed build() {
            built = true;
            WindowsPerformanceCounterFeed result = new WindowsPerformanceCounterFeed(this);
            result.setEntity(checkNotNull(entity, "entity"));
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("WindowsPerformanceCounterFeed.Builder created, but build() never called");
        }
    }

    /**
     * For rebind; do not call directly; use builder
     */
    public WindowsPerformanceCounterFeed() {
    }

    protected WindowsPerformanceCounterFeed(Builder builder) {
        List<WindowsPerformanceCounterPollConfig<?>> polls = Lists.newArrayList();
        for (WindowsPerformanceCounterPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            WindowsPerformanceCounterPollConfig<?> configCopy = new WindowsPerformanceCounterPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period);
            polls.add(configCopy);
        }
        setConfig(POLLS, polls);
        initUniqueTag(builder.uniqueTag, polls);
    }

    @Override
    protected void preStart() {
        List<WindowsPerformanceCounterPollConfig<?>> polls = getConfig(POLLS);
        
        long minPeriod = Integer.MAX_VALUE;
        List<String> performanceCounterNames = Lists.newArrayList();
        for (WindowsPerformanceCounterPollConfig<?> config : polls) {
            minPeriod = Math.min(minPeriod, config.getPeriod());
            performanceCounterNames.add(config.getPerformanceCounterName());
        }
        
        Iterable<String> allParams = ImmutableList.<String>builder()
                .add("Get-Counter")
                .add("-Counter")
                .add(JOINER_ON_COMMA.join(Iterables.transform(performanceCounterNames, QuoteStringFunction.INSTANCE)))
                .add("-SampleInterval")
                .add("2") // TODO: This should be a config key
                .build();
        String command = String.format("(%s).CounterSamples.CookedValue", JOINER_ON_SPACE.join(allParams));
        log.debug("Windows performance counter poll command will be: {}", command);

        getPoller().scheduleAtFixedRate(new GetPerformanceCountersJob<WinRmToolResponse>(getEntity(), command),
                new SendPerfCountersToSensors(getEntity(), getConfig(POLLS)), 100L);
    }

    private static class GetPerformanceCountersJob<T> implements Callable<T> {

        private final Entity entity;
        private final String command;

        GetPerformanceCountersJob(Entity entity, String command) {
            this.entity = entity;
            this.command = command;
        }

        @Override
        public T call() throws Exception {
            WinRmMachineLocation machine = EffectorTasks.getWinRmMachine(entity);
            WinRmToolResponse response = machine.executePsScript(command);
            return (T)response;
        }
    }

    @SuppressWarnings("unchecked")
    protected Poller<WinRmToolResponse> getPoller() {
        return (Poller<WinRmToolResponse>) super.getPoller();
    }

    /**
     * A {@link java.util.concurrent.Callable} that wraps another {@link java.util.concurrent.Callable}, where the
     * inner {@link java.util.concurrent.Callable} is executed in the context of a
     * specific entity.
     *
     * @param <T> The type of the {@link java.util.concurrent.Callable}.
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
            final ExecutionContext executionContext

                    = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
            return executionContext.submit(Maps.newHashMap(), job).get();
        }
    }

    private static class SendPerfCountersToSensors implements PollHandler<WinRmToolResponse> {

        private final EntityLocal entity;
        private final List<WindowsPerformanceCounterPollConfig<?>> polls;

        public SendPerfCountersToSensors(EntityLocal entity, List<WindowsPerformanceCounterPollConfig<?>> polls) {
            this.entity = entity;
            this.polls = polls;
        }

        @Override
        public boolean checkSuccess(WinRmToolResponse val) {
            if (val.getStatusCode() != 0) return false;
            String stderr = val.getStdErr();
            if (stderr == null || stderr.length() != 0) return false;
            String out = val.getStdOut();
            if (out == null || out.length() == 0) return false;
            return true;
        }

        @Override
        public void onSuccess(WinRmToolResponse val) {
            String[] values = val.getStdOut().split("\r\n");
            for (int i = 0; i < polls.size(); i++) {
                Class<?> clazz = polls.get(i).getSensor().getType();
                Maybe<? extends Object> maybeValue = TypeCoercions.tryCoerce(values[i], TypeToken.of(clazz));
                Object value = maybeValue.isAbsent() ? null : maybeValue.get();
                AttributeSensor<Object> attribute = (AttributeSensor<Object>) Sensors.newSensor(clazz, polls.get(i).getSensor().getName(), polls.get(i).getDescription());
                entity.setAttribute(attribute, value);
            }
        }

        @Override
        public void onFailure(WinRmToolResponse val) {
            log.error("Windows Performance Counter query did not respond as expected. exitcode={} stdout={} stderr={}",
                    new Object[]{val.getStatusCode(), val.getStdOut(), val.getStdErr()});
            for (WindowsPerformanceCounterPollConfig<?> config : polls) {
                entity.setAttribute(Sensors.newSensor(config.getSensor().getClass(), config.getPerformanceCounterName(), config.getDescription()), null);
            }
        }

        @Override
        public void onException(Exception exception) {
            log.error("Detected exception while retrieving Windows Performance Counters from entity " +
                    entity.getDisplayName(), exception);
            for (WindowsPerformanceCounterPollConfig<?> config : polls) {
                entity.setAttribute(Sensors.newSensor(config.getSensor().getClass(), config.getPerformanceCounterName(), config.getDescription()), null);
            }
        }

        @Override
        public String getDescription() {
            return "" + polls;
        }

        @Override
        public String toString() {
            return super.toString()+"["+getDescription()+"]";
        }
    }

    /**
     * A poll handler that takes the result of the <tt>typeperf</tt> invocation and sets the appropriate sensors.
     */
    private static class SendPerfCountersToSensors2 implements PollHandler<SshPollValue> {

        private static final Set<? extends Class<? extends Number>> INTEGER_TYPES
                = ImmutableSet.of(Integer.class, Long.class, Byte.class, Short.class, BigInteger.class);

        private final EntityLocal entity;
        private final SortedMap<String, AttributeSensor> sensorMap;

        public SendPerfCountersToSensors2(EntityLocal entity, List<WindowsPerformanceCounterPollConfig<?>> polls) {
            this.entity = entity;
            
            sensorMap = Maps.newTreeMap();
            for (WindowsPerformanceCounterPollConfig<?> config : polls) {
                sensorMap.put(config.getPerformanceCounterName(), config.getSensor());
            }
        }

        @Override
        public boolean checkSuccess(SshPollValue val) {
            if (val.getExitStatus() != 0) return false;
            String stderr = val.getStderr();
            if (stderr == null || stderr.length() != 0) return false;
            String out = val.getStdout();
            if (out == null || out.length() == 0) return false;
            return true;
        }

        @Override
        public void onSuccess(SshPollValue val) {
            String stdout = val.getStdout();
            Matcher matcher = lineWithPerfData.matcher(stdout);
            if (!matcher.find()) {
                onFailure(val);
                return;
            }

            String group = matcher.group(0);
            Iterator<String> values = new PerfCounterValueIterator(group);
            for (AttributeSensor sensor : sensorMap.values()) {
                if (!values.hasNext()) {
                    // The perf counter response has fewer elements than expected
                    onFailure(val);
                    return;
                }
                String value = values.next();

                Class sensorType = sensor.getType();
                if (INTEGER_TYPES.contains(sensorType)) {
                    // Windows always returns decimal-formatted numbers (e.g. 1234.00000), even for integer counters.
                    // Integer.valueOf() throws a NumberFormatException if it sees something in that format. So for
                    // pure integer sensors, we truncate the decimal part.
                    int decimalAt = value.indexOf('.');
                    if (decimalAt >= 0)
                        value = value.substring(0, decimalAt);
                }
                entity.setAttribute(sensor, TypeCoercions.coerce(value, sensorType));
            }

            if (values.hasNext()) {
                // The perf counter response has more elements than expected
                onFailure(val);
                return;
            }
        }

        @Override
        public void onFailure(SshPollValue val) {
            log.error("Windows Performance Counter query did not respond as expected. exitcode={} stdout={} stderr={}",
                    new Object[]{val.getExitStatus(), val.getStdout(), val.getStderr()});
            for (AttributeSensor attribute : sensorMap.values()) {
                entity.setAttribute(attribute, null);
            }
        }

        @Override
        public void onException(Exception exception) {
            log.error("Detected exception while retrieving Windows Performance Counters from entity " +
                    entity.getDisplayName(), exception);
            for (AttributeSensor attribute : sensorMap.values()) {
                entity.setAttribute(attribute, null);
            }
        }
        
        @Override
        public String toString() {
            return super.toString()+"["+getDescription()+"]";
        }
        
        @Override
        public String getDescription() {
            return ""+sensorMap;
        }
    }

    static class PerfCounterValueIterator implements Iterator<String> {

        // This pattern matches the contents of the first field, and optionally matches the rest of the line as
        // further fields. Feed the second match back into the pattern again to get the next field, and repeat until
        // all fields are discovered.
        protected static final Pattern splitPerfData = Pattern.compile("^\"([^\\\"]*)\"((,\"[^\\\"]*\")*)$");

        private Matcher matcher;

        public PerfCounterValueIterator(String input) {
            matcher = splitPerfData.matcher(input);
            // Throw away the first element (the timestamp) (and also confirm that we have a pattern match)
            checkArgument(hasNext(), "input "+input+" does not match expected pattern "+splitPerfData.pattern());
            next();
        }

        @Override
        public boolean hasNext() {
            return matcher != null && matcher.find();
        }

        @Override
        public String next() {
            String next = matcher.group(1);

            String remainder = matcher.group(2);
            if (!Strings.isNullOrEmpty(remainder)) {
                assert remainder.startsWith(",");
                remainder = remainder.substring(1);
                matcher = splitPerfData.matcher(remainder);
            } else {
                matcher = null;
            }

            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static enum QuoteStringFunction implements Function<String, String> {
        INSTANCE;

        @Nullable
        @Override
        public String apply(@Nullable String input) {
            return input != null ? "\"" + input + "\"" : null;
        }
    }
}
