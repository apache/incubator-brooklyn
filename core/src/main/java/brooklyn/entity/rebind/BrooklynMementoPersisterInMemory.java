package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.Serializers;

import com.google.common.base.Throwables;

public class BrooklynMementoPersisterInMemory extends AbstractBrooklynMementoPersister {

    private final ClassLoader classLoader;
    
    BrooklynMementoPersisterInMemory(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        super.checkpoint(newMemento);
        reserializeMemento();
    }

    @Override
    public void delta(Delta delta) {
        super.delta(delta);
        reserializeMemento();
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
