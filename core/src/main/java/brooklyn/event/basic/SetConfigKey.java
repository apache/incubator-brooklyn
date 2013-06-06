package brooklyn.event.basic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.ListConfigKey.ListModification;
import brooklyn.event.basic.ListConfigKey.ListModifications;
import brooklyn.management.ExecutionContext;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.Sets;

/** A config key representing a list of values. 
 * If a value is set on this key, it is _added_ to the list.
 * (But a warning is issued if a collection is passed in.)
 * <p>
 * To add all items in a collection, to add a collection as a single element, 
 * to clear the list, or to set a collection (clearing first), 
 * use the relevant {@link ListModification} in {@link ListModifications}.
 * <p>  
 * Specific values can be added in a replaceable way by referring to a subkey.
 */
//TODO Create interface
public class SetConfigKey<V> extends BasicConfigKey<Set<? extends V>> implements StructuredConfigKey {

    private static final long serialVersionUID = 751024268729803210L;
    private static final Logger log = LoggerFactory.getLogger(SetConfigKey.class);
    
    public final Class<V> subType;

    public SetConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public SetConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public SetConfigKey(Class<V> subType, String name, String description, Set<? extends V> defaultValue) {
        super((Class)Set.class, name, description, defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey() {
        String subName = Identifiers.makeRandomId(8);
        return new SubElementConfigKey<V>(this, subType, getName()+"."+subName, "element of "+getName()+", uid "+subName, null);
    }

    @Override
    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this.equals(((SubElementConfigKey<?>) contender).parent));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        Set<V> result = Sets.newLinkedHashSet();
        for (Object k : vals.keySet()) {
            if (isSubKey(k))
                result.add( ((SubElementConfigKey<V>) k).extractValue(vals, exec) );
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public boolean isSet(Map<?, ?> vals) {
        if (vals.containsKey(this)) return true;
        for (Object contender : vals.keySet()) {
            if (isSubKey(contender)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Object applyValueToMap(Object value, Map target) {
        if (value instanceof StructuredModification) {
            return ((StructuredModification)value).applyToKeyInMap(this, target);
        } else  if (value instanceof Collection) {
            String warning = "Discouraged undecorated setting of a collection to SetConfigKey "+this+": use SetModification.{set,add}. " +
            		"Defaulting to 'add'. Look at debug logging for call stack.";
            log.warn(warning);
            if (log.isDebugEnabled())
                log.debug("Trace for: "+warning, new Throwable("Trace for: "+warning));
            for (Object v: (Collection)value) 
                applyValueToMap(v, target);
            return null;
        } else {
            // just add to set, using anonymous key
            target.put(subKey(), value);
            return null;
        }
    }
    
    public interface SetModification<T> extends StructuredModification<SetConfigKey<T>>, Set<T> {
    }
    
    public static class SetModifications extends StructuredModifications {
        /** when passed as a value to a SetConfigKey, causes each of these items to be added.
         * if you have just one, no need to wrap in a mod. */
        // to prevent confusion (e.g. if a set is passed) we require two objects here.
        public static final <T> SetModification<T> add(final T o1, final T o2, final T ...oo) {
            Set<T> l = new LinkedHashSet<T>();
            l.add(o1); l.add(o2);
            for (T o: oo) l.add(o);
            return new SetModificationBase<T>(l, false);
        }
        /** when passed as a value to a SetConfigKey, causes each of these items to be added */
        public static final <T> SetModification<T> addAll(final Collection<T> items) { 
            return new SetModificationBase<T>(items, false);
        }
        /** when passed as a value to a SetConfigKey, causes the items to be added as a single element in the set */
        public static final <T> SetModification<T> addItem(final T item) {
            return new SetModificationBase<T>(Collections.singleton(item), false);
        }
        /** when passed as a value to a SetConfigKey, causes the set to be cleared and these items added */
        public static final <T> SetModification<T> set(final Collection<T> items) { 
            return new SetModificationBase<T>(items, true);
        }
    }

    @SuppressWarnings("serial")
    public static class SetModificationBase<T> extends LinkedHashSet<T> implements SetModification<T> {
        private final boolean clearFirst;
        public SetModificationBase(Collection<T> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Object applyToKeyInMap(SetConfigKey<T> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            for (T o: this) target.put(key.subKey(), o);
            return null;
        }
    }
}
