package brooklyn.entity.proxying;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.policy.Policy;

import com.google.common.annotations.Beta;

/**
 * Gives details of an entity to be created. It describes the entity's configuration, and is
 * reusable to create multiple entities with the same configuration.
 * 
 * @author aled
 *
 * @param <T> The type of entity to be created
 * 
 * @see BasicEntitySpec for instantiating this; users who need to implement this are strongly encouraged
 *      to extend BasicEntitySpec.
 */
public interface EntitySpec<T extends Entity> {

    /**
     * @return The type of the entity
     */
    public Class<T> getType();

    /**
     * @return The display name of the entity
     */
    public String getDisplayName();
    
    /**
     * @return The implementation of the entity; if not null. this overrides any defaults or other configuration
     * 
     * @see ImplementedBy on the entity interface classes for how defaults are defined.
     * @see EntityTypeRegistry for how implementations can be defined globally
     */
    @Nullable
    public Class<? extends T> getImplementation();

    /**
     * @return The entity's parent
     */
    public Entity getParent();
    
    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the entity type
     */
    public Map<String, ?> getFlags();

    /**
     * @return Read-only configuration values
     */
    public Map<ConfigKey<?>, Object> getConfig();
    
    public List<Policy> getPolicies();
}
