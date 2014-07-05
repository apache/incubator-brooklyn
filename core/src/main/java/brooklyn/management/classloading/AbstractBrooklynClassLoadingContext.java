package brooklyn.management.classloading;

import brooklyn.management.ManagementContext;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;

public abstract class AbstractBrooklynClassLoadingContext implements BrooklynClassLoadingContext {

    protected final ManagementContext mgmt;

    public AbstractBrooklynClassLoadingContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    @Override
    public ManagementContext getManagementContext() {
        return mgmt;
    }
    
    @Override
    public Class<?> loadClass(String className) {
        return tryLoadClass(className).get();
    }

    @Override
    // this is the only one left for subclasses
    public abstract Maybe<Class<?>> tryLoadClass(String className);

    @Override
    public <T> Class<? extends T> loadClass(String className, Class<T> type) {
        return tryLoadClass(className, type).get();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <T> Maybe<Class<? extends T>> tryLoadClass(String className, Class<T> type) {
        Maybe<Class<?>> result = tryLoadClass(className);
        if (result.isAbsent()) return (Maybe)result;
        Class<?> clazz = result.get();
        if (type.isAssignableFrom(clazz)) return (Maybe)result;
        throw new ClassCastException(className+" is not an instance of "+type);
    }

    @Override
    public abstract String toString();

    @Override
    public int hashCode() {
        return Objects.hashCode(mgmt);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BrooklynClassLoadingContext)) return false;
        if (!Objects.equal(mgmt, ((BrooklynClassLoadingContext)obj).getManagementContext())) return false;
        return true;
    }
    
}
