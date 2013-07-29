package brooklyn.entity.proxying;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.policy.Policy;

/**
 * Gives details of an entity to be created. It describes the entity's configuration, and is
 * reusable to create multiple entities with the same configuration.
 * 
 * To create an EntitySpec, it is strongly encouraged to use {@link brooklyn.entity.proxying.EntitySpecs}.
 * Users who need to implement this are strongly encouraged to extend 
 * {@link brooklyn.entity.proxying.BasicEntitySpec}.
 * 
 * @param <T> The type of entity to be created
 * 
 * @author aled
 */
public interface EntitySpec<T extends Entity> extends Serializable {

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

    public Set<Class<?>> getAdditionalInterfaces();

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
