package brooklyn.management.ha;

import java.io.File;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import com.google.common.base.Throwables;

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

    public void registerBundle(String bundleUrl) {
        try {
            Osgis.install(framework, bundleUrl);
        } catch (BundleException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> Maybe<Class<T>> tryResolveClass(String bundleUrl, String type) {
        try {
            Maybe<Bundle> bundle = Osgis.getBundle(framework, bundleUrl);
            if (bundle.isPresent()) {
                Class<T> clazz = (Class<T>) bundle.get().loadClass(type);
                return Maybe.of(clazz);
            } else {
                return Maybe.absent("No bundle found in " + framework + " at URL: " + bundleUrl);
            }
        } catch (ClassNotFoundException e) {
            return Maybe.absent(e);
        }
    }

}
