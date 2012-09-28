package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.Serializers;

import com.google.common.base.Throwables;

public class BrooklynMementoPersisterInMemory implements BrooklynMementoPersister {

    private final ClassLoader classLoader;
    private volatile MutableBrooklynMemento memento = new MutableBrooklynMemento();
    
    BrooklynMementoPersisterInMemory(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
    }
    
    @Override
    public BrooklynMemento loadMemento() {
        // Trusting people not to cast+modify, because the in-memory persister wouldn't be used in production code
        return memento;
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        checkNotNull(newMemento, "memento");
        try {
            memento.reset(newMemento);
            memento = Serializers.reconstitute(memento, classLoader);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void delta(Delta delta) {
        memento.removeEntities(delta.removedEntityIds());
        memento.removeLocations(delta.removedLocationIds());
        memento.updateEntityMementos(delta.entityMementos());
        memento.updateLocationMementos(delta.locationMementos());
    }
}
