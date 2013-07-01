package brooklyn.event.feed.ssh;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

public class SshPollConfig<T> extends PollConfig<SshPollValue, T, SshPollConfig<T>> {

    private String command;
    private Map<String,String> env = Maps.newLinkedHashMap();

    public static final Predicate<SshPollValue> DEFAULT_SUCCESS = new Predicate<SshPollValue>() {
        @Override
        public boolean apply(@Nullable SshPollValue input) {
            return input != null && input.getExitStatus() == 0;
        }};

    public SshPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        super.checkSuccess(DEFAULT_SUCCESS);
    }

    public SshPollConfig(SshPollConfig<T> other) {
        super(other);
        command = other.command;
        env = other.env;
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

    /**
     * Overrides any Function given to {@link #checkSuccess}. If argument
     * is true feed treats any non-zero response code as a failure. Otherwise
     * sets {@link #checkSuccess} to {@link Predicates#alwaysTrue()}.
     *
     * @deprecated since 0.6; default is true, see {@link #checkSuccess}
     */
    @Deprecated
    public SshPollConfig<T> failOnNonZeroResultCode(boolean val) {
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
    public SshPollConfig<T> failOnNonZeroResultCode() {
        return failOnNonZeroResultCode(true);
    }
}
