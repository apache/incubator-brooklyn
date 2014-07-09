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
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityLocal entity;
        private SortedMap<String, AttributeSensor> sensors = Maps.newTreeMap(Ordering.natural());
        private long period = 30;
        private TimeUnit periodUnits = TimeUnit.SECONDS;
        private volatile boolean built;

        public Builder entity(EntityLocal val) {
            this.entity = checkNotNull(val, "entity");
            return this;
        }
        public Builder addSensor(String performanceCounterName, AttributeSensor sensor) {
            sensors.put(checkNotNull(performanceCounterName, "performanceCounterName"),
                    checkNotNull(sensor, "sensor"));
            return this;
        }
        public Builder addSensors(Map<String, AttributeSensor> sensors) {
            sensors.putAll(checkNotNull(sensors, "sensors"));
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
        public WindowsPerformanceCounterFeed build() {
            built = true;
            WindowsPerformanceCounterFeed result = new WindowsPerformanceCounterFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("WindowsPerformanceCounterFeed.Builder created, but build() never called");
        }
    }

    private final EntityLocal entity;
    private final long period;
    private final TimeUnit periodUnits;
    private final SortedMap<String, AttributeSensor> attributeSensors;
    private final ProcessTaskFactory<Integer> taskFactory;

    protected WindowsPerformanceCounterFeed(Builder builder) {
        super(checkNotNull(builder.entity, "builder.entity"));
        entity = builder.entity;
        period = builder.period;
        periodUnits = builder.periodUnits;
        attributeSensors = builder.sensors;
        SshMachineLocation machine = EffectorTasks.getSshMachine(getEntity());
        Iterable<String> allParams = ImmutableList.<String>builder()
                .add("typeperf")
                .addAll(Iterables.transform(attributeSensors.keySet(), QuoteStringFunction.INSTANCE))
                .add("-sc")
                .add("1")
                .build();
        String command = JOINER_ON_SPACE.join(allParams);
        log.debug("Windows performance counter poll command will be: {}", command);
        taskFactory = SshTasks.newSshExecTaskFactory(machine, command)
                .allowingNonZeroExitCode()
                .runAsCommand();
    }

    @Override
    protected void preStart() {
        final Callable<SshPollValue> queryForCounterValues = new Callable<SshPollValue>() {
            public SshPollValue call() throws Exception {
                ProcessTaskWrapper<Integer> taskWrapper = taskFactory.newTask();
                final ExecutionContext executionContext =
                        ((EntityInternal) entity).getManagementSupport().getExecutionContext();
                executionContext.submit(taskWrapper);
                taskWrapper.block();
                Optional<Integer> exitCode = Optional.fromNullable(taskWrapper.getExitCode());
                return new SshPollValue(null, exitCode.or(-1), taskWrapper.getStdout(), taskWrapper.getStderr());
            }
        };

        ((Poller<SshPollValue>) poller).scheduleAtFixedRate(
                new CallInEntityExecutionContext<SshPollValue>(entity, queryForCounterValues),
                new SendPerfCountersToSensors(entity, attributeSensors),
                periodUnits.toMillis(period));
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

    /**
     * A poll handler that takes the result of the <tt>typeperf</tt> invocation and sets the appropriate sensors.
     */
    private static class SendPerfCountersToSensors implements PollHandler<SshPollValue> {

        private static final Set<? extends Class<? extends Number>> INTEGER_TYPES
                = ImmutableSet.of(Integer.class, Long.class, Byte.class, Short.class, BigInteger.class);

        private SortedMap<String, AttributeSensor> sensorMap;
        private EntityLocal entity;

        public SendPerfCountersToSensors(EntityLocal entity, SortedMap<String, AttributeSensor> sensorMap) {
            this.sensorMap = sensorMap;
            this.entity = entity;
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
