package brooklyn.entity.proxying;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.util.javalang.Reflections;

/**
 * For creating {@link EntitySpec} instances.
 * 
 * @author aled
 * 
 * @deprecated since 0.6; use {@link EntitySpec#create(Class)} etc
 */
public class EntitySpecs {

    private EntitySpecs() {}
    
    /**
     * @deprecated use {@link EntitySpec#create(Class)} 
     */
    public static <T extends Entity> BasicEntitySpec<T,?> spec(Class<T> type) {
        return BasicEntitySpec.newInstance(type);
    }
    
    /**
     * @deprecated use {@link EntitySpec#create(Class, Class)} 
     */
    public static <T extends Entity, U extends T> BasicEntitySpec<T,?> spec(Class<T> type, Class<U> implType) {
        return BasicEntitySpec.newInstance(type, implType);
    }
    
    /**
     * @deprecated use {@link EntitySpec#create(Map, Class)}
     */
    public static <T extends Entity> BasicEntitySpec<T,?> spec(Map<?,?> config, Class<T> type) {
        return EntitySpecs.spec(type).configure(config);
    }
    
    /**
     * Creates a new EntitySpec for this application type. If the type is an interface, then the returned spec
     * will use the normal logic of looking for {@link ImplementedBy} etc. 
     * 
     * However, if the type is a class then the that implementation will be used directly. When an entity is
     * created using the EntitySpec, one will get back a proxy of type {@link StartableApplication}, but the
     * proxy will also implement all the other interfaces that the given type class implements.
     */
    @SuppressWarnings("unchecked")
    public static <T extends StartableApplication> BasicEntitySpec<StartableApplication, ?> appSpec(Class<? extends T> type) {
        if (type.isInterface()) {
            return (BasicEntitySpec<StartableApplication, ?>) EntitySpecs.spec(type);
        } else {
            // is implementation
            Set<Class<?>> additionalInterfaceClazzes = Reflections.getInterfacesIncludingClassAncestors(type);
            return EntitySpecs.spec(StartableApplication.class, type)
                    .additionalInterfaces(additionalInterfaceClazzes);
        }
    }

    /**
     * Wraps an entity spec so its configuration can be overridden without modifying the 
     * original entity spec.
     */
    public static <T extends Entity> WrappingEntitySpec<T> wrapSpec(EntitySpec<? extends T> spec) {
        return WrappingEntitySpec.newInstance(spec);
    }
}
