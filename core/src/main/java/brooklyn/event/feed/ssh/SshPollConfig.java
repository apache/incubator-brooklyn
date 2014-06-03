package brooklyn.event.feed.ssh;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;

public class SshPollConfig<T> extends PollConfig<SshPollValue, T, SshPollConfig<T>> {

    private Supplier<String> commandSupplier;
    private List<Supplier<Map<String,String>>> dynamicEnvironmentSupplier = MutableList.of();
    private Map<String,String> fixedEnvironment = Maps.newLinkedHashMap();

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
        commandSupplier = other.commandSupplier;
        fixedEnvironment = other.fixedEnvironment;
    }
    
    /** @deprecated since 0.7.0; use {@link #getCommandSupplier()} and resolve just-in-time */
    public String getCommand() {
        return getCommandSupplier().get();
    }
    public Supplier<String> getCommandSupplier() {
        return commandSupplier;
    }
    
    /** @deprecated since 0.7.0; use {@link #getEnvSupplier()} and resolve just-in-time */
    public Map<String, String> getEnv() {
        return getEnvSupplier().get();
    }
    public Supplier<Map<String,String>> getEnvSupplier() {
        return new Supplier<Map<String,String>>() {
            @Override
            public Map<String, String> get() {
                Map<String,String> result = MutableMap.of();
                result.putAll(fixedEnvironment);
                for (Supplier<Map<String, String>> envS: dynamicEnvironmentSupplier) {
                    if (envS!=null) {
                        Map<String, String> envM = envS.get();
                        if (envM!=null) {
                            // TODO deeply additive?
                            result.putAll(envM);
                        }
                    }
                }
                return result;
            }
        };
    }
    

    public SshPollConfig<T> command(String val) { return command(Suppliers.ofInstance(val)); }
    public SshPollConfig<T> command(Supplier<String> val) {
        this.commandSupplier = val;
        return this;
    }

    /** add the given env param */
    public SshPollConfig<T> env(String key, String val) {
        fixedEnvironment.put(checkNotNull(key, "key"), checkNotNull(val, "val"));
        return this;
    }
    
    /** add the given env params; sequence is as per {@link #env(Supplier)} */
    public SshPollConfig<T> env(Map<String,String> val) {
        return env(Suppliers.ofInstance(val));
    }

    /** adds the given dynamic env supplier. 
     * these are put into the result in the order they are applied, with fixed values
     * set via {@link #env(String, String)} preceding all these.
     * <p>
     * currently addition is not deep, in the case of two identical top-level keys 
     * the latter one kills anything from the former. 
     * this behaviour may change in future. */
    public SshPollConfig<T> env(Supplier<Map<String,String>> val) {
        dynamicEnvironmentSupplier.add(val);
        return this;
    }

}
