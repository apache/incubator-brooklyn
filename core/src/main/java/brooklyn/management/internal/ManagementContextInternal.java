package brooklyn.management.internal;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;

public interface ManagementContextInternal extends ManagementContext {

    public static final String EFFECTOR_TAG = "EFFECTOR";
    public static final String NON_TRANSIENT_TASK_TAG = "NON-TRANSIENT";
    public static final String TRANSIENT_TASK_TAG = "TRANSIENT";

    public static final ConfigKey<String> BROOKLYN_CATALOG_URL = ConfigKeys.newStringConfigKey("brooklyn.catalog.url",
            "The URL of a catalog.xml descriptor; absent for default (~/.brooklyn/catalog.xml), " +
            "or empty for no URL (use default scanner)", "file://~/.brooklyn/catalog.xml");
    
    ClassLoader getBaseClassLoader();

    Iterable<URL> getBaseClassPathForScanning();

    void setBaseClassPathForScanning(Iterable<URL> urls);

    void addEntitySetListener(CollectionChangeListener<Entity> listener);

    void removeEntitySetListener(CollectionChangeListener<Entity> listener);

    void terminate();
    
    long getTotalEffectorInvocations();

    <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException;
    
    <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters);

    BrooklynStorage getStorage();
}
