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
import brooklyn.location.basic.LocationInternal;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableList;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
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
                // TODO See RebindManagerImpl.rebind for dummyLookupContext; remove duplication
                BrooklynMementoPersisterToMultiFile persister = new BrooklynMementoPersisterToMultiFile(tempDir , classLoader);
                persister.checkpoint(memento);
                LookupContext dummyLookupContext = new LookupContext() {
                    @Override public Entity lookupEntity(Class<?> type, String id) {
                        List<Class<?>> types = MutableList.<Class<?>>of(Entity.class, EntityInternal.class, EntityProxy.class);
                        if (type != null) types.add(type);
                        return (Entity) newDummy(types);
                    }
                    @Override public Location lookupLocation(Class<?> type, String id) {
                        List<Class<?>> types = MutableList.<Class<?>>of(Location.class, LocationInternal.class);
                        if (type != null) types.add(type);
                        return (Location) newDummy(types);
                    }
                    @Override public Policy lookupPolicy(Class<?> type, String id) {
                        List<Class<?>> types = MutableList.<Class<?>>of(Policy.class);
                        if (type != null) types.add(type);
                        return (Policy) newDummy(types);
                    }
                    @Override public Enricher lookupEnricher(Class<?> type, String id) {
                        List<Class<?>> types = MutableList.<Class<?>>of(Enricher.class);
                        if (type != null) types.add(type);
                        return (Enricher) newDummy(types);
                    }
                    private Object newDummy(List<Class<?>> types) {
                        return java.lang.reflect.Proxy.newProxyInstance(
                            classLoader,
                            types.toArray(new Class<?>[types.size()]),
                            new InvocationHandler() {
                                @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                                    return m.invoke(this, args);
                                }
                            });
                    }
                };

                // Not actually reconstituting, because need to use a real lookupContext to reconstitute all the entities
                RebindExceptionHandler exceptionHandler = new RebindExceptionHandlerImpl(RebindManager.RebindFailureMode.FAIL_AT_END, RebindManager.RebindFailureMode.FAIL_AT_END);
                persister.loadMemento(dummyLookupContext, exceptionHandler);
            } finally {
                Os.tryDeleteDirectory(tempDir.getAbsolutePath());
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
