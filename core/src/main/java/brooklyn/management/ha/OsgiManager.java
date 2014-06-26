package brooklyn.management.ha;

import java.io.File;

import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.osgi.Osgis;

public class OsgiManager {

    public static final ConfigKey<Boolean> USE_OSGI = BrooklynServerConfig.USE_OSGI;
    
    /* see Osgis for info on starting framework etc */
    
    protected Framework framework;
    protected File osgiTempDir;
    
    public void start() {
        try {
            // TODO any extra startup args?
            // TODO dir to come from brooklyn properties;
            // note dir must be different for each if starting multiple instances
            osgiTempDir = Os.newTempDir("brooklyn-osgi-cache");
            framework = Osgis.newFrameworkStarted(osgiTempDir.getAbsolutePath(), false, MutableMap.of());
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public void stop() {
        try {
            if (framework!=null)
                framework.stop();
        } catch (BundleException e) {
            throw Exceptions.propagate(e);
        }
        osgiTempDir = Os.deleteRecursively(osgiTempDir).asNullOrThrowing();
        framework = null;
    }

    // TODO: throws BundleException appropriate?
    public void registerBundle(String bundleUrl) throws BundleException {
        Osgis.install(framework, bundleUrl);
    }

    // TODO: Handle .get failing
    public <T> Maybe<Class<T>> tryResolveClass(String bundleUrl, String type) {
        try {
            Class<T> clazz = (Class<T>) Osgis.getBundle(framework, bundleUrl).get().loadClass(type);
            return Maybe.of(clazz);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return Maybe.absent(e);
        }
    }
}
