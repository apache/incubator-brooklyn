package brooklyn.event.feed.ssh;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;

import com.google.common.collect.Maps;

public class SshPollConfig<T> extends PollConfig<SshPollValue, T, SshPollConfig<T>> {

    private String command;
    private Map<String,String> env = Maps.newLinkedHashMap();
    private boolean failOnNonZeroResultCode;
    
    public SshPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public SshPollConfig(SshPollConfig<T> other) {
        super(other);
        command = other.command;
        env = other.env;
        failOnNonZeroResultCode = other.failOnNonZeroResultCode;
    }
    
    public String getCommand() {
        return command;
    }
    
    public Map<String, String> getEnv() {
        return env;
    }

    public boolean isFailOnNonZeroResultCode() {
        return failOnNonZeroResultCode;
    }
    
    public SshPollConfig<T> command(String val) {
        this.command = val;
        return this;
    }

    public SshPollConfig<T> env(String key, String val) {
        env.put(checkNotNull(key, "key"), checkNotNull(val, "val"));
        return this;
    }
    
    public SshPollConfig<T> env(Map<String,String> val) {
        for (Map.Entry<String, String> entry : checkNotNull(val, "map").entrySet()) {
            env(entry.getKey(), entry.getValue());
        }
        return this;
    }
    
    public SshPollConfig<T> failOnNonZeroResultCode(boolean val) {
        this.failOnNonZeroResultCode = val;
        return this;
    }
    
    public SshPollConfig<T> failOnNonZeroResultCode() {
        return failOnNonZeroResultCode(true);
    }
}
