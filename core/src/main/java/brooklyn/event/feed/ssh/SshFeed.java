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
package brooklyn.event.feed.ssh;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Provides a feed of attribute values, by polling over ssh.
 * 
 * Example usage (e.g. in an entity that extends SoftwareProcessImpl):
 * <pre>
 * {@code
 * private SshFeed feed;
 * 
 * //@Override
 * protected void connectSensors() {
 *   super.connectSensors();
 *   
 *   feed = SshFeed.builder()
 *       .entity(this)
 *       .machine(mySshMachineLachine)
 *       .poll(new SshPollConfig<Boolean>(SERVICE_UP)
 *           .command("rabbitmqctl -q status")
 *           .onSuccess(new Function<SshPollValue, Boolean>() {
 *               public Boolean apply(SshPollValue input) {
 *                 return (input.getExitStatus() == 0);
 *               }}))
 *       .build();
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
public class SshFeed extends AbstractFeed {

    public static final Logger log = LoggerFactory.getLogger(SshFeed.class);

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private EntityLocal entity;
        private boolean onlyIfServiceUp = false;
        private Supplier<SshMachineLocation> machine;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<SshPollConfig<?>> polls = Lists.newArrayList();
        private boolean execAsCommand = false;
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
        /** optional, to force a machine; otherwise it is inferred from the entity */
        public Builder machine(SshMachineLocation val) { return machine(Suppliers.ofInstance(val)); }
        /** optional, to force a machine; otherwise it is inferred from the entity */
        public Builder machine(Supplier<SshMachineLocation> val) {
            this.machine = val;
            return this;
        }
        public Builder period(Duration period) {
            return period(period.toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return this;
        }
        public Builder poll(SshPollConfig<?> config) {
            polls.add(config);
            return this;
        }
        public Builder execAsCommand() {
            execAsCommand = true;
            return this;
        }
        public Builder execAsScript() {
            execAsCommand = false;
            return this;
        }
        public SshFeed build() {
            built = true;
            SshFeed result = new SshFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("SshFeed.Builder created, but build() never called");
        }
    }
    
    private static class SshPollIdentifier {
        final Supplier<String> command;
        final Supplier<Map<String, String>> env;

        private SshPollIdentifier(Supplier<String> command, Supplier<Map<String, String>> env) {
            this.command = checkNotNull(command, "command");
            this.env = checkNotNull(env, "env");
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(command, env);
        }
        
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SshPollIdentifier)) {
                return false;
            }
            SshPollIdentifier o = (SshPollIdentifier) other;
            return Objects.equal(command, o.command) &&
                    Objects.equal(env, o.env);
        }
    }
    
    private final Supplier<SshMachineLocation> machine;
    private final boolean execAsCommand;
    
    // Treat as immutable once built
    private final SetMultimap<SshPollIdentifier, SshPollConfig<?>> polls = HashMultimap.<SshPollIdentifier,SshPollConfig<?>>create();

    /** @deprecated since 0.7.0, use static convenience on {@link Locations} */
    @Deprecated
    public static SshMachineLocation getMachineOfEntity(Entity entity) {
        return Machines.findUniqueSshMachineLocation(entity.getLocations()).orNull();
    }

    protected SshFeed(final Builder builder) {
        super(builder.entity, builder.onlyIfServiceUp);
        machine = builder.machine != null ? builder.machine : 
            new Supplier<SshMachineLocation>() {
                @Override
                public SshMachineLocation get() {
                    return Locations.findUniqueSshMachineLocation(entity.getLocations()).get();
                }
            };
        execAsCommand = builder.execAsCommand;
        
        for (SshPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            SshPollConfig<?> configCopy = new SshPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            polls.put(new SshPollIdentifier(config.getCommandSupplier(), config.getEnvSupplier()), configCopy);
        }
    }

    @Override
    protected void preStart() {
        for (final SshPollIdentifier pollInfo : polls.keySet()) {
            Set<SshPollConfig<?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<? super SshPollValue>> handlers = Sets.newLinkedHashSet();

            for (SshPollConfig<?> config : configs) {
                handlers.add(new AttributePollHandler<SshPollValue>(config, entity, this));
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }
            
            getPoller().scheduleAtFixedRate(
                    new Callable<SshPollValue>() {
                        public SshPollValue call() throws Exception {
                            return exec(pollInfo.command.get(), pollInfo.env.get());
                        }}, 
                    new DelegatingPollHandler<SshPollValue>(handlers),
                    minPeriod);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Poller<SshPollValue> getPoller() {
        return (Poller<SshPollValue>) poller;
    }
    
    private SshPollValue exec(String command, Map<String,String> env) throws IOException {
        if (log.isTraceEnabled()) log.trace("Ssh polling for {}, executing {} with env {}", new Object[] {machine, command, env});
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitStatus;
        ConfigBag flags = ConfigBag.newInstance()
            .configure(SshTool.PROP_NO_EXTRA_OUTPUT, true)
            .configure(SshTool.PROP_OUT_STREAM, stdout)
            .configure(SshTool.PROP_ERR_STREAM, stderr);
        if (execAsCommand) {
            exitStatus = machine.get().execCommands(flags.getAllConfig(),
                    "ssh-feed", ImmutableList.of(command), env);
        } else {
            exitStatus = machine.get().execScript(flags.getAllConfig(),
                    "ssh-feed", ImmutableList.of(command), env);
        }

        return new SshPollValue(machine.get(), exitStatus, new String(stdout.toByteArray()), new String(stderr.toByteArray()));
    }
}
