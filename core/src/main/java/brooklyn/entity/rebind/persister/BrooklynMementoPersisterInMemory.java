package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.Serializers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

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
    public void waitForWritesCompleted() throws InterruptedException {
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
            memento = Serializers.reconstitute(memento, classLoader);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}
