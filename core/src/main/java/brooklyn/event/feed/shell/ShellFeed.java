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
package brooklyn.event.feed.shell;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.management.ExecutionContext;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.internal.SystemProcessTaskFactory.ConcreteSystemProcessTaskFactory;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Provides a feed of attribute values, by executing shell commands (on the local machine where 
 * this instance of brooklyn is running). Useful e.g. for paas tools such as Cloud Foundry vmc 
 * which operate against a remote target.
 * 
 * Example usage (e.g. in an entity that extends SoftwareProcessImpl):
 * <pre>
 * {@code
 * private ShellFeed feed;
 * 
 * //@Override
 * protected void connectSensors() {
 *   super.connectSensors();
 *   
 *   feed = ShellFeed.builder()
 *       .entity(this)
 *       .machine(mySshMachineLachine)
 *       .poll(new ShellPollConfig<Long>(DISK_USAGE)
 *           .command("df -P | grep /dev")
 *           .failOnNonZeroResultCode(true)
 *           .onSuccess(new Function<SshPollValue, Long>() {
 *                public Long apply(SshPollValue input) {
 *                  String[] parts = input.getStdout().split("[ \\t]+");
 *                  return Long.parseLong(parts[2]);
 *                }}))
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
 * @see SshFeed (to run on remote machines)
 * @see FunctionFeed (for arbitrary functions)
 * 
 * @author aled
 */
public class ShellFeed extends AbstractFeed {

    public static final Logger log = LoggerFactory.getLogger(ShellFeed.class);

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private EntityLocal entity;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<ShellPollConfig<?>> polls = Lists.newArrayList();
        private volatile boolean built;
        
        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return this;
        }
        public Builder poll(ShellPollConfig<?> config) {
            polls.add(config);
            return this;
        }
        public ShellFeed build() {
            built = true;
            ShellFeed result = new ShellFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("ShellFeed.Builder created, but build() never called");
        }
    }
    
    private static class ShellPollIdentifier {
        final String command;
        final Map<String, String> env;
        final File dir;
        final String input;
        final String context;
        final long timeout;

        private ShellPollIdentifier(String command, Map<String, String> env, File dir, String input, String context, long timeout) {
            this.command = checkNotNull(command, "command");
            this.env = checkNotNull(env, "env");
            this.dir = dir;
            this.input = input;
            this.context = checkNotNull(context, "context");
            this.timeout = timeout;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(command, env, dir, input, timeout);
        }
        
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ShellPollIdentifier)) {
                return false;
            }
            ShellPollIdentifier o = (ShellPollIdentifier) other;
            return Objects.equal(command, o.command) &&
                    Objects.equal(env, o.env) &&
                    Objects.equal(dir, o.dir) &&
                    Objects.equal(input, o.input) &&
                    Objects.equal(timeout, o.timeout);
        }
    }
    
    // Treat as immutable once built
    private final SetMultimap<ShellPollIdentifier, ShellPollConfig<?>> polls = HashMultimap.<ShellPollIdentifier,ShellPollConfig<?>>create();
    
    protected ShellFeed(Builder builder) {
        super(builder.entity);
        
        for (ShellPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ShellPollConfig<?> configCopy = new ShellPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            String command = config.getCommand();
            Map<String, String> env = config.getEnv();
            File dir = config.getDir();
            String input = config.getInput();
            String context = config.getSensor().getName();
            long timeout = config.getTimeout();

            polls.put(new ShellPollIdentifier(command, env, dir, input, context, timeout), configCopy);
        }
    }

    @Override
    protected void preStart() {
        for (final ShellPollIdentifier pollInfo : polls.keySet()) {
            Set<ShellPollConfig<?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<? super SshPollValue>> handlers = Sets.newLinkedHashSet();

            for (ShellPollConfig<?> config : configs) {
                handlers.add(new AttributePollHandler<SshPollValue>(config, entity, this));
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }

            final ProcessTaskFactory<?> taskFactory = newTaskFactory(pollInfo.command, pollInfo.env, pollInfo.dir, 
                    pollInfo.input, pollInfo.context, pollInfo.timeout);
            final ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();

            getPoller().scheduleAtFixedRate(
                    new Callable<SshPollValue>() {
                        @Override public SshPollValue call() throws Exception {
                            ProcessTaskWrapper<?> taskWrapper = taskFactory.newTask();
                            executionContext.submit(taskWrapper);
                            taskWrapper.block();
                            Optional<Integer> exitCode = Optional.fromNullable(taskWrapper.getExitCode());
                            return new SshPollValue(null, exitCode.or(-1), taskWrapper.getStdout(), taskWrapper.getStderr());
                        }}, 
                    new DelegatingPollHandler<SshPollValue>(handlers), 
                    minPeriod);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Poller<SshPollValue> getPoller() {
        return (Poller<SshPollValue>) poller;
    }
    
    /**
     * Executes the given command (using `bash -l -c $command`, so as to have a good path set).
     * 
     * @param command The command to execute
     * @param env     Environment variable settings, in format name=value
     * @param dir     Working directory, or null to inherit from current process
     * @param input   Input to send to the command (if not null)
     */
    protected ProcessTaskFactory<?> newTaskFactory(final String command, Map<String,String> env, File dir, String input, final String summary, final long timeout) {
        // FIXME Add generic timeout() support to task ExecutionManager
        if (timeout > 0) {
            log.warn("Timeout ({}ms) not currently supported for ShellFeed {}", timeout, this);
        }

        return new ConcreteSystemProcessTaskFactory<Object>(command)
                .environmentVariables(env)
                .loginShell(true)
                .directory(dir)
                .runAsCommand()
                .summary(summary);
    }
}
