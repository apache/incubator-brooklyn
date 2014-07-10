package brooklyn.management.classloading;

import java.net.URL;

import com.google.common.base.Objects;

import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

public class JavaBrooklynClassLoadingContext extends AbstractBrooklynClassLoadingContext {

    private final ClassLoader loader;

    public JavaBrooklynClassLoadingContext(ManagementContext mgmt, ClassLoader loader) {
        super(mgmt);
        this.loader = loader;
    }
    
    public static JavaBrooklynClassLoadingContext newDefault(ManagementContext mgmt) {
        ClassLoader cl = null;
        if (mgmt!=null) cl = mgmt.getCatalog().getRootClassLoader();
        if (cl==null) cl = JavaBrooklynClassLoadingContext.class.getClassLoader();
        return new JavaBrooklynClassLoadingContext(mgmt, cl);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Maybe<Class<?>> tryLoadClass(String className) {
        try {
            return (Maybe) Maybe.of(loader.loadClass(className));
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return Maybe.absent(e);
        }
    }

    @Override
    public String toString() {
        return "java:"+loader;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), loader);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof JavaBrooklynClassLoadingContext)) return false;
        if (!Objects.equal(loader, ((JavaBrooklynClassLoadingContext)obj).loader)) return false;
        return true;
    }

    @Override
    public URL getResource(String name) {
        return loader.getResource(name);
    }
    
}
