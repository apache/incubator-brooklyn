package brooklyn.event.feed.shell;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.event.feed.ssh.SshPollValue;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

public class ShellPollConfig<T> extends PollConfig<SshPollValue, T, ShellPollConfig<T>> {

    private String command;
    private Map<String,String> env = Maps.newLinkedHashMap();
    private long timeout = -1;
    private File dir;
    private String input;

    public static final Predicate<SshPollValue> DEFAULT_SUCCESS = new Predicate<SshPollValue>() {
        @Override
        public boolean apply(@Nullable SshPollValue input) {
            return input != null && input.getExitStatus() == 0;
        }};

    public ShellPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        super.checkSuccess(DEFAULT_SUCCESS);
    }

    public ShellPollConfig(ShellPollConfig<T> other) {
        super(other);
        command = other.command;
        env = other.env;
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

    /** @deprecated since 0.6; default is true, see {@link #checkSuccess} */
    @Deprecated
    public boolean isFailOnNonZeroResultCode() {
        return super.getCheckSuccess().equals(DEFAULT_SUCCESS);
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
    
    /**
     * Overrides any Function given to {@link #checkSuccess}. If argument
     * is true feed treats any non-zero response code as a failure. Otherwise
     * sets {@link #checkSuccess} to {@link Predicates#alwaysTrue()}.
     *
     * @deprecated since 0.6; default is true, see {@link #checkSuccess}
     */
    @Deprecated
    public ShellPollConfig<T> failOnNonZeroResultCode(boolean val) {
        if (val) {
            super.checkSuccess(DEFAULT_SUCCESS);
        } else {
            super.checkSuccess(Predicates.alwaysTrue());
        }
        return this;
    }
    
    /**
     * Overrides any Function given to {@link #checkSuccess} to treat any
     * non-zero response code as a failure.
     *
     * @deprecated since 0.6; default is true, see {@link #checkSuccess}
     */
    @Deprecated
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
