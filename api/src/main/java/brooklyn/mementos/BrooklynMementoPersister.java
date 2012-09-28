package brooklyn.mementos;

import java.util.Collection;

public interface BrooklynMementoPersister {

    BrooklynMemento loadMemento();
    
    void checkpoint(BrooklynMemento memento);
    
    void delta(Delta delta);
    
    public interface Delta {
        Collection<LocationMemento> locationMementos();
        Collection<EntityMemento> entityMementos();
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
    }
}
