package brooklyn.management.classloading;

import java.net.URL;
import java.util.List;

import brooklyn.management.ManagementContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;

public class OsgiBrooklynClassLoadingContext extends AbstractBrooklynClassLoadingContext {

    private final List<String> bundles;

    public OsgiBrooklynClassLoadingContext(ManagementContext mgmt, List<String> bundles) {
        super(mgmt);
        this.bundles = bundles;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Maybe<Class<?>> tryLoadClass(String className) {
        Maybe<Class<Object>> clazz = null;
        Maybe<OsgiManager> osgi = null;
        if (mgmt!=null) {
            osgi = ((ManagementContextInternal)mgmt).getOsgiManager();
            if (osgi.isPresent() && bundles!=null && !bundles.isEmpty()) {
                clazz = osgi.get().tryResolveClass(className, bundles);
                if (clazz.isPresent())
                    return (Maybe)clazz;
            }
        }
        
        if (clazz!=null) { 
            // if OSGi bundles were defined and failed, then use its error message
            return (Maybe)clazz;
        }
        // else determine best message
        if (mgmt==null) return Maybe.absent("No mgmt context available for loading "+className);
        if (osgi!=null && osgi.isAbsent()) return Maybe.absent("OSGi not available on mgmt for loading "+className);
        if (bundles==null || bundles.isEmpty())
            return Maybe.absent("No bundles available for loading "+className);
        return Maybe.absent("Inconsistent state ("+mgmt+"/"+osgi+"/"+bundles+" loading "+className);
    }

    @Override
    public String toString() {
        return "OSGi:"+bundles;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), bundles);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof OsgiBrooklynClassLoadingContext)) return false;
        if (!Objects.equal(bundles, ((OsgiBrooklynClassLoadingContext)obj).bundles)) return false;
        return true;
    }

    @Override
    public URL getResource(String name) {
        if (mgmt!=null) {
            Maybe<OsgiManager> osgi = ((ManagementContextInternal)mgmt).getOsgiManager();
            if (osgi.isPresent() && bundles!=null && !bundles.isEmpty()) {
                return osgi.get().getResource(name, bundles);
            }
        }
        return null;
    }
    
}
