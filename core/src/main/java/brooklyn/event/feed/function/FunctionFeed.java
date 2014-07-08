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
package brooklyn.event.feed.function;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Provides a feed of attribute values, by periodically invoking functions.
 * 
 * Example usage (e.g. in an entity that extends SoftwareProcessImpl):
 * <pre>
 * {@code
 * private FunctionFeed feed;
 * 
 * //@Override
 * protected void connectSensors() {
 *   super.connectSensors();
 *   
 *   feed = FunctionFeed.builder()
 *     .entity(this)
 *     .poll(new FunctionPollConfig<Object, Boolean>(SERVICE_UP)
 *         .period(500, TimeUnit.MILLISECONDS)
 *         .callable(new Callable<Boolean>() {
 *             public Boolean call() throws Exception {
 *               return getDriver().isRunning();
 *             }
 *         })
 *         .onExceptionOrFailure(Functions.constant(Boolan.FALSE))
 *     .build();
 * }
 * 
 * {@literal @}Override
 * protected void disconnectSensors() {
 *   super.disconnectSensors();
 *   if (feed != null) feed.stop();
 * }
 * }
 * </pre>
 * 
 * @author aled
 */
public class FunctionFeed extends AbstractFeed {

    private static final Logger log = LoggerFactory.getLogger(FunctionFeed.class);

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private EntityLocal entity;
        private boolean onlyIfServiceUp = false;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<FunctionPollConfig<?,?>> polls = Lists.newArrayList();
        private volatile boolean built;

        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder onlyIfServiceUp() { return onlyIfServiceUp(true); }
        public Builder onlyIfServiceUp(boolean onlyIfServiceUp) { 
            this.onlyIfServiceUp = onlyIfServiceUp; 
            return this; 
        }
        public Builder period(Duration d) {
            return period(d.toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return this;
        }
        public Builder poll(FunctionPollConfig<?,?> config) {
            polls.add(config);
            return this;
        }
        public FunctionFeed build() {
            built = true;
            FunctionFeed result = new FunctionFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("FunctionFeed.Builder created, but build() never called");
        }
    }
    
    private static class FunctionPollIdentifier {
        final Callable<?> job;

        private FunctionPollIdentifier(Callable<?> job) {
            this.job = checkNotNull(job, "job");
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(job);
        }
        
        @Override
        public boolean equals(Object other) {
            return (other instanceof FunctionPollIdentifier) && Objects.equal(job, ((FunctionPollIdentifier)other).job);
        }
    }
    
    // Treat as immutable once built
    private final SetMultimap<FunctionPollIdentifier, FunctionPollConfig<?,?>> polls = HashMultimap.<FunctionPollIdentifier,FunctionPollConfig<?,?>>create();
    
    protected FunctionFeed(Builder builder) {
        super(builder.entity, builder.onlyIfServiceUp);
        
        for (FunctionPollConfig<?,?> config : builder.polls) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            FunctionPollConfig<?,?> configCopy = new FunctionPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            Callable<?> job = config.getCallable();
            polls.put(new FunctionPollIdentifier(job), configCopy);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void preStart() {
        for (final FunctionPollIdentifier pollInfo : polls.keySet()) {
            Set<FunctionPollConfig<?,?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<?>> handlers = Sets.newLinkedHashSet();

            for (FunctionPollConfig<?,?> config : configs) {
                handlers.add(new AttributePollHandler(config, entity, this));
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }
            
            getPoller().scheduleAtFixedRate(
                    (Callable)pollInfo.job,
                    new DelegatingPollHandler(handlers), 
                    minPeriod);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Poller<Object> getPoller() {
        return (Poller<Object>) poller;
    }
}
