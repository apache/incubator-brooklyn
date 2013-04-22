package brooklyn.entity.nosql.redis;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
public class RedisStoreImpl extends SoftwareProcessImpl implements RedisStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class);

    private transient SshFeed sshFeed;

    public RedisStoreImpl() {
        this(MutableMap.of(), null);
    }
    public RedisStoreImpl(Map properties) {
        this(properties, null);
    }
    public RedisStoreImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisStoreImpl(Map properties, Entity parent) {
        super(properties, parent);
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        sshFeed = SshFeed.builder()
                .entity(this)
                .poll(new SshPollConfig<Integer>(UPTIME)
                        .command(getDriver().getRunDir() + "/bin/redis-cli info")
                        .onError(Functions.constant(-1))
                        .onSuccess(Functions.compose(new Function<String, Integer>(){
                            @Override
                            public Integer apply(@Nullable String input) {
                                Optional<String> line = Iterables.tryFind(Splitter.on('\n').split(input), Predicates.containsPattern("uptime_in_seconds:"));
                                if (line.isPresent()) {
                                    String data = line.get().trim();
                                    int colon = data.indexOf(":");
                                    return Integer.parseInt(data.substring(colon + 1));
                                } else {
                                    throw new IllegalStateException();
                                }
                            }
                        }, SshValueFunctions.stdout())))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (sshFeed != null && sshFeed.isActivated()) sshFeed.stop();
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

}
