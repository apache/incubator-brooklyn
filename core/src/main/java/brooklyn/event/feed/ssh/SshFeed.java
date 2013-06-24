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

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
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
        private SshMachineLocation machine;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<SshPollConfig<?>> polls = Lists.newArrayList();
        private volatile boolean built;
        
        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder machine(SshMachineLocation val) {
            this.machine = val;
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
        public Builder poll(SshPollConfig<?> config) {
            polls.add(config);
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
        final String command;
        final Map<String, String> env;

        private SshPollIdentifier(String command, Map<String, String> env) {
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
    
    private final SshMachineLocation machine;
    
    // Treat as immutable once built
    private final SetMultimap<SshPollIdentifier, SshPollConfig<?>> polls = HashMultimap.<SshPollIdentifier,SshPollConfig<?>>create();
    
    protected SshFeed(Builder builder) {
        super(builder.entity);
        machine = checkNotNull(builder.machine, "machine");
        
        for (SshPollConfig<?> config : builder.polls) {
            SshPollConfig<?> configCopy = new SshPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            String command = config.getCommand();
            Map<String, String> env = config.getEnv();
            polls.put(new SshPollIdentifier(command, env), configCopy);
        }
    }

    @Override
    protected void preStart() {
        for (final SshPollIdentifier pollInfo : polls.keySet()) {
            Set<SshPollConfig<?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<SshPollValue>> handlers = Sets.newLinkedHashSet();

            for (SshPollConfig<?> config : configs) {
                AttributePollHandler<SshPollValue> handler;
                if (config.isFailOnNonZeroResultCode()) {
                    handler = new AttributePollHandler<SshPollValue>(config, entity, this) {
                        @Override public void onSuccess(SshPollValue val) {
                            if (val.getExitStatus() == 0) {
                                super.onSuccess(val);
                            } else {
                                onException(new Exception("Exit status "+val.getExitStatus()));
                            }
                        }
                    };
                } else {
                    handler = new AttributePollHandler<SshPollValue>(config, entity, this);
                }
                handlers.add(handler);
                
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }
            
            Callable<SshPollValue> pollJob;
            
            getPoller().scheduleAtFixedRate(
                    new Callable<SshPollValue>() {
                        public SshPollValue call() throws Exception {
                            return exec(pollInfo.command, pollInfo.env);
                        }}, 
                    new DelegatingPollHandler(handlers), 
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

        int exitStatus = machine.run(MutableMap.of("out", stdout, "err", stderr), command, env);

        return new SshPollValue(machine, exitStatus, new String(stdout.toByteArray()), new String(stderr.toByteArray()));
    }
}
