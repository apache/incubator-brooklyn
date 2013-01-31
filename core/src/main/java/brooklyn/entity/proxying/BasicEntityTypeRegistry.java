package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import brooklyn.entity.Entity;
import brooklyn.util.exceptions.Exceptions;

public class BasicEntityTypeRegistry implements EntityTypeRegistry {

    private final Map<Class<?>, Class<?>> registry = new ConcurrentHashMap<Class<?>, Class<?>>();
    private final Map<Class<?>, Class<?>> cache = new ConcurrentHashMap<Class<?>, Class<?>>();
    
    @Override
    public <T extends Entity> EntityTypeRegistry registerImplementation(Class<T> type, Class<? extends T> implClazz) {
        checkNotNull(type, "type");
        checkNotNull(implClazz, "implClazz");
        checkIsImplementation(type, implClazz);
        checkIsNewStyleImplementation(implClazz);
        
        registry.put(type, implClazz);
        cache.remove(type);
        return this;
    }
    
    @Override
    public <T extends Entity> Class<? extends T> getImplementedBy(Class<T> type) {
        Class<?> result = cache.get(type);
        if (result != null) {
            return (Class<? extends T>) result;
        }
        
        result = registry.get(type);
        if (result == null) {
            result = getFromAnnotation(type);
        }
        cache.put(type, result);
        return (Class<? extends T>) result;
    }

    private <T extends Entity> Class<? extends T> getFromAnnotation(Class<T> type) {
        ImplementedBy annotation = type.getAnnotation(brooklyn.entity.proxying.ImplementedBy.class);
        if (annotation == null) throw new IllegalArgumentException("Interface "+type+" is not annotated with @"+ImplementedBy.class.getSimpleName()+", and no implementation is registered");
        Class<? extends Entity> value = annotation.value();
        checkIsImplementation(type, value);
        return (Class<? extends T>) value;
    }
    
    private void checkIsImplementation(Class<?> type, Class<?> implClazz) {
        if (!type.isAssignableFrom(implClazz)) throw new IllegalStateException("Implementation "+implClazz+" does not implement "+type);
        if (implClazz.isInterface()) throw new IllegalStateException("Implementation "+implClazz+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(implClazz.getModifiers())) throw new IllegalStateException("Implementation "+implClazz+" is abstract, but must be a non-abstract class");
    }
    
    private void checkIsNewStyleImplementation(Class<?> implClazz) {
        try {
            implClazz.getConstructor(new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Implementation "+implClazz+" must have a no-argument constructor");
        } catch (SecurityException e) {
            throw Exceptions.propagate(e);
        }
    }
}
