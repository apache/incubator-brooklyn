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
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.ExecutionContext;
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
import brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
    private static final int OUTPUT_COLUMN_WIDTH = 100;

    @SuppressWarnings("serial")
    public static final ConfigKey<Collection<WindowsPerformanceCounterPollConfig<?>>> POLLS = ConfigKeys.newConfigKey(
            new TypeToken<Collection<WindowsPerformanceCounterPollConfig<?>>>() {},
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
            if (!config.isEnabled()) continue;
            @SuppressWarnings({ "unchecked", "rawtypes" })
            WindowsPerformanceCounterPollConfig<?> configCopy = new WindowsPerformanceCounterPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period);
            polls.add(configCopy);
        }
        config().set(POLLS, polls);
        initUniqueTag(builder.uniqueTag, polls);
    }

    @Override
    protected void preStart() {
        Collection<WindowsPerformanceCounterPollConfig<?>> polls = getConfig(POLLS);
        
        long minPeriod = Integer.MAX_VALUE;
        List<String> performanceCounterNames = Lists.newArrayList();
        for (WindowsPerformanceCounterPollConfig<?> config : polls) {
            minPeriod = Math.min(minPeriod, config.getPeriod());
            performanceCounterNames.add(config.getPerformanceCounterName());
        }
        
        Iterable<String> allParams = ImmutableList.<String>builder()
                .add("(Get-Counter")
                .add("-Counter")
                .add(JOINER_ON_COMMA.join(Iterables.transform(performanceCounterNames, QuoteStringFunction.INSTANCE)))
                .add("-SampleInterval")
                .add("2") // TODO: extract SampleInterval as a config key
                .add(").CounterSamples")
                .add("|")
                .add("Format-Table")
                .add(String.format("@{Expression={$_.Path};width=%d},@{Expression={$_.CookedValue};width=%<d}", OUTPUT_COLUMN_WIDTH))
                .add("-HideTableHeaders")
                .add("|")
                .add("Out-String")
                .add("-Width")
                .add(String.valueOf(OUTPUT_COLUMN_WIDTH * 2))
                .build();
        String command = JOINER_ON_SPACE.join(allParams);
        log.debug("Windows performance counter poll command for {} will be: {}", entity, command);

        GetPerformanceCountersJob<WinRmToolResponse> job = new GetPerformanceCountersJob(getEntity(), command);
        getPoller().scheduleAtFixedRate(
                new CallInEntityExecutionContext(entity, job),
                new SendPerfCountersToSensors(getEntity(), polls),
                minPeriod);
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
            ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
            return executionContext.submit(Maps.newHashMap(), job).get();
        }
    }

    @VisibleForTesting
    static class SendPerfCountersToSensors implements PollHandler<WinRmToolResponse> {
        private final EntityLocal entity;
        private final List<WindowsPerformanceCounterPollConfig<?>> polls;
        private final Set<AttributeSensor<?>> failedAttributes = Sets.newLinkedHashSet();
        private static final Pattern MACHINE_NAME_LOOKBACK_PATTERN = Pattern.compile(String.format("(?<=\\\\\\\\.{0,%d})\\\\.*", OUTPUT_COLUMN_WIDTH));
        
        public SendPerfCountersToSensors(EntityLocal entity, Collection<WindowsPerformanceCounterPollConfig<?>> polls) {
            this.entity = entity;
            this.polls = ImmutableList.copyOf(polls);
        }

        @Override
        public boolean checkSuccess(WinRmToolResponse val) {
            // TODO not just using statusCode; also looking at absence of stderr.
            // Status code is (empirically) unreliable: it returns 0 sometimes even when failed 
            // (but never returns non-zero on success).
            if (val.getStatusCode() != 0) return false;
            String stderr = val.getStdErr();
            if (stderr == null || stderr.length() != 0) return false;
            String out = val.getStdOut();
            if (out == null || out.length() == 0) return false;
            return true;
        }

        @Override
        public void onSuccess(WinRmToolResponse val) {
            for (String pollResponse : val.getStdOut().split("\r\n")) {
                if (Strings.isNullOrEmpty(pollResponse)) {
                    continue;
                }
                String path = pollResponse.substring(0, OUTPUT_COLUMN_WIDTH - 1);
                // The performance counter output prepends the sensor name with "\\<machinename>" so we need to remove it
                Matcher machineNameLookbackMatcher = MACHINE_NAME_LOOKBACK_PATTERN.matcher(path);
                if (!machineNameLookbackMatcher.find()) {
                    continue;
                }
                String name = machineNameLookbackMatcher.group(0).trim();
                String rawValue = pollResponse.substring(OUTPUT_COLUMN_WIDTH).replaceAll("^\\s+", "");
                WindowsPerformanceCounterPollConfig<?> config = getPollConfig(name);
                Class<?> clazz = config.getSensor().getType();
                AttributeSensor<Object> attribute = (AttributeSensor<Object>) Sensors.newSensor(clazz, config.getSensor().getName(), config.getDescription());
                try {
                    Object value = TypeCoercions.coerce(rawValue, TypeToken.of(clazz));
                    entity.setAttribute(attribute, value);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    if (failedAttributes.add(attribute)) {
                        log.warn("Failed to coerce value '{}' to {} for {} -> {}", new Object[] {rawValue, clazz, entity, attribute});
                    } else {
                        if (log.isTraceEnabled()) log.trace("Failed (repeatedly) to coerce value '{}' to {} for {} -> {}", new Object[] {rawValue, clazz, entity, attribute});
                    }
                }
            }
        }

        @Override
        public void onFailure(WinRmToolResponse val) {
            log.error("Windows Performance Counter query did not respond as expected. exitcode={} stdout={} stderr={}",
                    new Object[]{val.getStatusCode(), val.getStdOut(), val.getStdErr()});
            for (WindowsPerformanceCounterPollConfig<?> config : polls) {
                Class<?> clazz = config.getSensor().getType();
                AttributeSensor<?> attribute = Sensors.newSensor(clazz, config.getSensor().getName(), config.getDescription());
                entity.setAttribute(attribute, null);
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

        private WindowsPerformanceCounterPollConfig<?> getPollConfig(String sensorName) {
            for (WindowsPerformanceCounterPollConfig<?> poll : polls) {
                if (poll.getPerformanceCounterName().equalsIgnoreCase(sensorName)) {
                    return poll;
                }
            }
            throw new IllegalStateException(String.format("%s not found in configured polls: %s", sensorName, polls));
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
