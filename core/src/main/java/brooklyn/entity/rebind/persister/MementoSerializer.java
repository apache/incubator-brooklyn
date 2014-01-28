package brooklyn.entity.rebind.persister;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

public interface MementoSerializer<T> {
    String toString(T memento);
    T fromString(String string);
    void setLookupContext(LookupContext lookupContext);
    void unsetLookupContext();
}