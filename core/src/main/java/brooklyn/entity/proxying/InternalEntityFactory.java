package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.ManagementContext;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;

import com.google.common.collect.Maps;

/**
 * Creates entities (and proxies) of required types, given the 
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * @author aled
 */
public class InternalEntityFactory {

    private final ManagementContext managementContext;
    private final EntityTypeRegistry entityTypeRegistry;

    /**
     * For tracking if AbstractEntity constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing entities directly (and expecting configure() to be
     * called inside the constructor, etc).
     * 
     * @author aled
     */
    public static class FactoryConstructionTracker {
        private static ThreadLocal<Boolean> constructing = new ThreadLocal<Boolean>();
        
        public static boolean isConstructing() {
            return (constructing.get() == Boolean.TRUE);
        }
        
        static void reset() {
            constructing.set(Boolean.FALSE);
        }
        
        static void setConstructing() {
            constructing.set(Boolean.TRUE);
        }
    }
    
    
    public InternalEntityFactory(ManagementContext managementContext, EntityTypeRegistry entityTypeRegistry) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.entityTypeRegistry = checkNotNull(entityTypeRegistry, "entityTypeRegistry");
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> T createEntityProxy(Class<T> type, T entity) {
        // TODO Don't want the proxy to have to implement EntityLocal, but required by how 
        // AbstractEntity.parent is used (e.g. parent.getAllConfig)
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[] { type, EntityProxy.class, EntityLocal.class },
                new EntityProxyImpl(entity));
    }

    public <T extends Entity> T createEntity(EntitySpec<T> spec) {
        if (spec.getFlags().containsKey("parent") || spec.getFlags().containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+spec);
        }
        
        try {
            Class<? extends T> clazz = getImplementedBy(spec);
            
            FactoryConstructionTracker.setConstructing();
            T entity;
            try {
                entity = construct(clazz, spec);
            } finally {
                FactoryConstructionTracker.reset();
            }
            
            if (isNewStyleEntity(clazz)) {
                ((AbstractEntity)entity).setManagementContext(managementContext);
                ((AbstractEntity)entity).setProxy(createEntityProxy(spec.getType(), entity));
                ((AbstractEntity)entity).configure(MutableMap.copyOf(spec.getFlags()));
            }
            
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((EntityLocal)entity).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }
            for (Map.Entry<HasConfigKey<?>, Object> entry : spec.getConfig2().entrySet()) {
                ((EntityLocal)entity).setConfig((HasConfigKey)entry.getKey(), entry.getValue());
            }
            ((AbstractEntity)entity).postConstruct();
            
            for (Policy policy : spec.getPolicies()) {
                ((EntityLocal)entity).addPolicy((AbstractPolicy)policy);
            }
            
            Entity parent = spec.getParent();
            if (parent != null) {
                parent = (parent instanceof AbstractEntity) ? ((AbstractEntity)parent).getProxyIfAvailable() : parent;
                entity.setParent(parent);
            }
            return entity;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private <T extends Entity> Class<? extends T> getImplementedBy(EntitySpec<T> spec) {
        if (spec.getImplementation() != null) {
            return spec.getImplementation();
        } else {
            return entityTypeRegistry.getImplementedBy(spec.getType());
        }
    }
    
    private boolean isNewStyleEntity(Class<? extends Entity> clazz) {
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    private <T extends Entity> T construct(Class<? extends T> clazz, EntitySpec<T> spec) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (isNewStyleEntity(clazz)) {
            return clazz.newInstance();
        } else {
            return constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
        }
    }
    
    private <T extends Entity> T constructOldStyle(Class<? extends T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (flags.containsKey("parent") || flags.containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+clazz);
        }
        Constructor<? extends T> c2 = Reflections.findCallabaleConstructor(clazz, new Object[] {flags});
        if (c2 != null) {
            return c2.newInstance(Maps.newLinkedHashMap(flags));
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
}
