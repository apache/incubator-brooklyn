package brooklyn.entity.rebind.persister;

import brooklyn.management.ha.ManagementNodeSyncRecord;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

/** Serializes the given object; it is often used with {@link BrooklynMemento} for persisting and restoring,
 * though it can be used for any object (and is also used for the {@link ManagementNodeSyncRecord} instances) */
public interface MementoSerializer<T> {
    
    public static final MementoSerializer<String> NOOP = new MementoSerializer<String>() {
        @Override
        public String toString(String memento) {
            return memento;
        }
        @Override
        public String fromString(String string) {
            return string;
        }
        @Override
        public void setLookupContext(LookupContext lookupContext) {
            // no-op
        }
        @Override
        public void unsetLookupContext() {
            // no-op
        }
    };
    
    String toString(T memento);
    T fromString(String string);
    void setLookupContext(LookupContext lookupContext);
    void unsetLookupContext();
}