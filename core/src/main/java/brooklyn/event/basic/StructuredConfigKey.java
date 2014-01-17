package brooklyn.event.basic;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public interface StructuredConfigKey {
    
    /** for internal use */
    Object applyValueToMap(Object value, @SuppressWarnings("rawtypes") Map target);
    
    boolean acceptsKeyMatch(Object contender);
    boolean acceptsSubkey(Object contender);
    boolean acceptsSubkeyStronglyTyped(Object contender);

    public static class StructuredModifications {
        /** when passed as a value to a StructuredConfigKey, causes the structure to be cleared */
        @SuppressWarnings("unchecked")
        public static final <U extends StructuredConfigKey,T extends StructuredModification<U>> T clearing() {
            return (T) new StructuredModification<U>() {
                @SuppressWarnings("rawtypes")
                @Override
                public Object applyToKeyInMap(U key, Map target) {
                    Set keysToRemove = new LinkedHashSet();
                    for (Object k : target.keySet()) {
                        if (key.acceptsKeyMatch(k) || key.acceptsSubkey(k))
                            keysToRemove.add(k);
                    }
                    for (Object k : keysToRemove) {
                        target.remove(k);
                    }
                    return null;
                }
            };
        }
    }
    
    public interface StructuredModification<T extends StructuredConfigKey> {
        Object applyToKeyInMap(T key, @SuppressWarnings("rawtypes") Map target);
    }
    
}
