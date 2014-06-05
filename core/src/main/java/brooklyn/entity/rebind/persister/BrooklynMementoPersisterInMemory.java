package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * @deprecated since 0.7.0 for production use {@link BrooklynMementoPersisterToMultiFile} instead; class be moved to tests
 */
public class BrooklynMementoPersisterInMemory extends AbstractBrooklynMementoPersister {

    private final ClassLoader classLoader;
    private final boolean checkPersistable;
    
    public BrooklynMementoPersisterInMemory(ClassLoader classLoader) {
        this(classLoader, true);
    }
    
    public BrooklynMementoPersisterInMemory(ClassLoader classLoader, boolean checkPersistable) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        this.checkPersistable = checkPersistable;
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        // TODO Could wait for concurrent checkpoint/delta, but don't need to for tests
        // because first waits for checkpoint/delta to have been called by RebindManagerImpl.
        return;
    }

    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        super.checkpoint(newMemento);
        if (checkPersistable) reserializeMemento();
    }

    @Override
    public void delta(Delta delta) {
        super.delta(delta);
        if (checkPersistable) reserializeMemento();
    }
    
    private void reserializeMemento() {
        // To confirm always serializable
        try {
            File tempDir = Files.createTempDir();
            try {
                // TODO Duplicate code for LookupContext in RebindManager
                BrooklynMementoPersisterToMultiFile persister = new BrooklynMementoPersisterToMultiFile(tempDir , classLoader);
                RebindExceptionHandler exceptionHandler = new RebindExceptionHandlerImpl(RebindManager.RebindFailureMode.FAIL_AT_END, RebindManager.RebindFailureMode.FAIL_AT_END);
                persister.checkpoint(memento);
                final BrooklynMementoManifest manifest = persister.loadMementoManifest(exceptionHandler);
                LookupContext dummyLookupContext = new LookupContext() {
                    @Override public Entity lookupEntity(String id) {
                        List<Class<?>> types = MutableList.<Class<?>>builder()
                                .add(Entity.class, EntityInternal.class, EntityProxy.class)
                                .add(loadClass(manifest.getEntityIdToType().get(id)))
                                .build();
                        return (Entity) java.lang.reflect.Proxy.newProxyInstance(
                                classLoader,
                                types.toArray(new Class<?>[types.size()]),
                                new InvocationHandler() {
                                    @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                                        return m.invoke(this, args);
                                    }
                                });
                    }
                    @Override public Location lookupLocation(String id) {
                        Class<?> clazz = loadClass(manifest.getLocationIdToType().get(id));
                        return (Location) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public Policy lookupPolicy(String id) {
                        Class<?> clazz = loadClass(manifest.getPolicyIdToType().get(id));
                        return (Policy) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public Enricher lookupEnricher(String id) {
                        Class<?> clazz = loadClass(manifest.getEnricherIdToType().get(id));
                        return (Enricher) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    private Class<?> loadClass(String name) {
                        try {
                            return classLoader.loadClass(name);
                        } catch (ClassNotFoundException e) {
                            throw Exceptions.propagate(e);
                        }
                    }
                    private <T> T invokeConstructor(Class<T> clazz, Object[]... possibleArgs) {
                        for (Object[] args : possibleArgs) {
                            try {
                                Optional<T> v = Reflections.invokeConstructorWithArgs(clazz, args, true);
                                if (v.isPresent()) {
                                    return v.get();
                                }
                            } catch (Exception e) {
                                throw Exceptions.propagate(e);
                            }
                        }
                        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
                    }
                };

                // Not actually reconstituting, because need to use a real lookupContext to reconstitute all the entities
                persister.loadMemento(dummyLookupContext, exceptionHandler);
            } finally {
                Os.tryDeleteDirectory(tempDir.getAbsolutePath());
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
