package brooklyn.policy.basic;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.BasicPolicyRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.trait.Configurable;
import brooklyn.management.ExecutionContext;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicyType;
import brooklyn.util.flags.FlagUtils;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Base {@link Policy} implementation; all policies should extend this or its children
 */
public abstract class AbstractPolicy extends AbstractEntityAdjunct implements Policy, Configurable {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class);

    protected String policyStatus;
    protected Map leftoverProperties = Maps.newLinkedHashMap();
    protected AtomicBoolean suspended = new AtomicBoolean(false);

    protected transient ExecutionContext execution;

    /**
     * The config values of this entity. Updating this map should be done
     * via getConfig/setConfig.
     */
    protected final PolicyConfigMap configsInternal = new PolicyConfigMap(this);

    private final PolicyType policyType = new PolicyTypeImpl(this);
    
    public AbstractPolicy() {
        this(Collections.emptyMap());
    }
    
    public AbstractPolicy(Map flags) {
        configure(flags);
        FlagUtils.checkRequiredFields(this);
    }

    /** will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * can be subclassed for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method. You must
     * *not* rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure() {
        configure(Collections.emptyMap());
    }
    
    protected void configure(Map flags) {
        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        // or if the value is a config key
        for (Iterator<Map.Entry> iter = flags.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = iter.next();
            if (entry.getKey() instanceof ConfigKey) {
                ConfigKey key = (ConfigKey)entry.getKey();
                if (getPolicyType().getConfigKeys().contains(key)) {
                    setConfig(key, entry.getValue());
                } else {
                    log.warn("Unknown configuration key {} for policy {}; ignoring", key, this);
                    iter.remove();
                }
            }
        }
        flags = FlagUtils.setConfigKeysFromFlags(flags, this);
        flags = FlagUtils.setFieldsFromFlags(flags, this);
        leftoverProperties.putAll(flags);

        //replace properties _contents_ with leftovers so subclasses see leftovers only
        flags.clear();
        flags.putAll(leftoverProperties);
        leftoverProperties = flags;
        
        if (!truth(name) && flags.containsKey("displayName")) {
            //'displayName' is a legacy way to refer to a location's name
            Preconditions.checkArgument(flags.get("displayName") instanceof CharSequence, "'displayName' property should be a string");
            setName(flags.remove("displayName").toString());
        }
    }
    
    public <T> T getConfig(ConfigKey<T> key) {
        return configsInternal.getConfig(key);
    }
    
    public <T> T setConfig(ConfigKey<T> key, T val) {
        if (entity != null && isRunning()) {
            doReconfigureConfig(key, val);
        }
        return (T) configsInternal.setConfig(key, val);
    }
    
    public Map<ConfigKey<?>, Object> getAllConfig() {
        return configsInternal.getAllConfig();
    }
    
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public PolicyType getPolicyType() {
        return policyType;
    }

    public void suspend() {
        suspended.set(true);
    }

    public void resume() {
        suspended.set(false);
    }

    public boolean isSuspended() {
        return suspended.get();
    }

    @Override
    public void destroy(){
        suspend();
        super.destroy();
    }

    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed();
    }

    @Override
    public RebindSupport<PolicyMemento> getRebindSupport() {
        return new BasicPolicyRebindSupport(this);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("name", name)
                .add("running", isRunning())
                .toString();
    }
}
