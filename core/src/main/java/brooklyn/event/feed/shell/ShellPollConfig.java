package brooklyn.event.feed.shell;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.event.feed.ssh.SshPollValue;

import com.google.common.collect.Maps;

public class ShellPollConfig<T> extends PollConfig<SshPollValue, T, ShellPollConfig<T>> {

    private String command;
    private Map<String,String> env = Maps.newLinkedHashMap();
    private boolean failOnNonZeroResultCode;
    private long timeout = -1;
    private File dir;
    private String input;
    
    public ShellPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public ShellPollConfig(ShellPollConfig<T> other) {
        super(other);
        command = other.command;
        env = other.env;
        failOnNonZeroResultCode = other.failOnNonZeroResultCode;
        timeout = other.timeout;
        dir = other.dir;
        input = other.input;
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

    public File getDir() {
        return dir;
    }

    public String getInput() {
        return input;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    public ShellPollConfig<T> command(String val) {
        this.command = val;
        return this;
    }

    public ShellPollConfig<T> env(String key, String val) {
        env.put(checkNotNull(key, "key"), checkNotNull(val, "val"));
        return this;
    }
    
    public ShellPollConfig<T> env(Map<String,String> val) {
        for (Map.Entry<String, String> entry : checkNotNull(val, "map").entrySet()) {
            env(entry.getKey(), entry.getValue());
        }
        return this;
    }
    
    public ShellPollConfig<T> failOnNonZeroResultCode(boolean val) {
        this.failOnNonZeroResultCode = val;
        return this;
    }
    
    public ShellPollConfig<T> failOnNonZeroResultCode() {
        return failOnNonZeroResultCode(true);
    }
    
    public ShellPollConfig<T> dir(File val) {
        this.dir = val;
        return this;
    }
    
    public ShellPollConfig<T> input(String val) {
        this.input = val;
        return this;
    }
    
    public ShellPollConfig<T> timeout(long timeout) {
        return timeout(timeout, TimeUnit.MILLISECONDS);
    }
    
    public ShellPollConfig<T> timeout(long timeout, TimeUnit units) {
        this.timeout = units.toMillis(timeout);
        return this;
    }
}
