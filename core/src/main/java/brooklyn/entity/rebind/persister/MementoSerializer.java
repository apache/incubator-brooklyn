package brooklyn.entity.rebind.persister;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

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