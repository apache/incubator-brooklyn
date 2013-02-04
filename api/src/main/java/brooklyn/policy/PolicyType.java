package brooklyn.policy;

import java.io.Serializable;
import java.util.Set;

import brooklyn.config.ConfigKey;

import com.google.common.annotations.Beta;

/**
 * Gives type information for an {@link Policy}. It is immutable.
 * 
 * For policies that can support config keys etc being added on-the-fly,
 * then this PolicyType will be a snapshot and subsequent snapshots will
 * include the changes.
 * 
 * @since 0.5
 */
@Beta
public interface PolicyType extends Serializable {

    /**
     * The type name of this policy (normally the fully qualified class name).
     */
    String getName();
    
    /**
     * ConfigKeys available on this policy.
     */
    Set<ConfigKey<?>> getConfigKeys();
    
    /**
     * The ConfigKey with the given name, or null if not found.
     */
    ConfigKey<?> getConfigKey(String name);
}
