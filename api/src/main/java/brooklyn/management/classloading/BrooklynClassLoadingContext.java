package brooklyn.management.classloading;

import brooklyn.management.ManagementContext;
import brooklyn.util.guava.Maybe;

/** 
 * Provides functionality for loading classes based on the current context
 * (e.g. catalog item, entity, etc). 
 */
public interface BrooklynClassLoadingContext {

    public ManagementContext getManagementContext();
    public Class<?> loadClass(String className);
    public <T> Class<? extends T> loadClass(String className, Class<T> type);
    
    public Maybe<Class<?>> tryLoadClass(String className);
    public <T> Maybe<Class<? extends T>> tryLoadClass(String className, Class<T> type);
    
}
