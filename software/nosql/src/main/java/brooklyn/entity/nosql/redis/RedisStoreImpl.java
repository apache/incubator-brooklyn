package brooklyn.entity.nosql.redis;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * An entity that represents a Redis key-value store service.
 */
public class RedisStoreImpl extends SoftwareProcessImpl implements RedisStore {
    private static final Logger LOG = LoggerFactory.getLogger(RedisStore.class);

    private transient SshFeed sshFeed;

    public RedisStoreImpl() {
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        // Find an SshMachineLocation for the UPTIME feed
        Optional<Location> location = Iterables.tryFind(getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        if (!location.isPresent()) throw new IllegalStateException("Could not find SshMachineLocation in list of locations");
        SshMachineLocation machine = (SshMachineLocation) location.get();
        String statsCommand = getDriver().getRunDir() + "/bin/redis-cli info stats";

        sshFeed = SshFeed.builder()
                .entity(this)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(UPTIME)
                        .command(getDriver().getRunDir() + "/bin/redis-cli info server")
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("uptime_in_seconds")))
                .poll(new SshPollConfig<Integer>(TOTAL_CONNECTIONS_RECEIVED)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("total_connections_received")))
                .poll(new SshPollConfig<Integer>(TOTAL_COMMANDS_PROCESSED)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("total_commands_processed")))
                .poll(new SshPollConfig<Integer>(EXPIRED_KEYS)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("expired_keys")))
                .poll(new SshPollConfig<Integer>(EVICTED_KEYS)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("evicted_keys")))
                .poll(new SshPollConfig<Integer>(KEYSPACE_HITS)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("keyspace_hits")))
                .poll(new SshPollConfig<Integer>(KEYSPACE_MISSES)
                        .command(statsCommand)
                        .onException(Functions.constant(-1))
                        .onFailure(Functions.constant(-1))
                        .onSuccess(infoFunction("keyspace_misses")))
                .build();
    }

    /**
     * Create a {@link Function} to retrieve a particular field value from a {@code redis-cli info}
     * command.
     * 
     * @param field the info field to retrieve and convert
     * @return a new function that converts a {@link SshPollValue} to an {@link Integer}
     */
    private static Function<SshPollValue, Integer> infoFunction(final String field) {
        return Functions.compose(new Function<String, Integer>() {
            @Override
            public Integer apply(@Nullable String input) {
                Optional<String> line = Iterables.tryFind(Splitter.on('\n').split(input), Predicates.containsPattern(field + ":"));
                if (line.isPresent()) {
                    String data = line.get().trim();
                    int colon = data.indexOf(":");
                    return Integer.parseInt(data.substring(colon + 1));
                } else {
                    throw new IllegalStateException("Data for field "+field+" not found: "+input);
                }
            }
        }, SshValueFunctions.stdout());
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (sshFeed != null) sshFeed.stop();
    }

    @Override
    public Class<?> getDriverInterface() {
        return RedisStoreDriver.class;
    }

    @Override
    public RedisStoreDriver getDriver() {
        return (RedisStoreDriver) super.getDriver();
    }

    @Override
    public String getAddress() {
        MachineLocation machine = getMachineOrNull();
        return (machine != null) ? machine.getAddress().getHostAddress() : null;
    }

    @Override
    public Integer getRedisPort() {
        return getAttribute(RedisStore.REDIS_PORT);
    }

}
