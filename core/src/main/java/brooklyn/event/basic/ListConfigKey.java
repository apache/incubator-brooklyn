package brooklyn.event.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.Lists;

/** A config key representing a list of values. 
 * If a value is set on this key, it is _added_ to the list.
 * (But a warning is issued if a collection is passed in.)
 * <p>
 * To add all items in a collection, to add a collection as a single element, 
 * to clear the list, or to set a collection (clearing first), 
 * use the relevant {@link ListModification} in {@link ListModifications}.
 * <p>  
 * Specific values can be added in a replaceable way by referring to a subkey.
 * 
 * @deprecated since 0.6; use SetConfigKey. The ListConfigKey no longer guarantees order
 */
//TODO Create interface
@Deprecated
public class ListConfigKey<V> extends BasicConfigKey<List<V>> implements StructuredConfigKey {

    private static final long serialVersionUID = 751024268729803210L;
    private static final Logger log = LoggerFactory.getLogger(ListConfigKey.class);
    
    public final Class<V> subType;

    public ListConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public ListConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ListConfigKey(Class<V> subType, String name, String description, List<? extends V> defaultValue) {
        super((Class)List.class, name, description, (List<V>)defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey() {
        String subName = Identifiers.makeRandomId(8);
        return new SubElementConfigKey<V>(this, subType, getName()+"."+subName, "element of "+getName()+", uid "+subName, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this.equals(((SubElementConfigKey<?>) contender).parent));
    }

    @SuppressWarnings("unchecked")
    public List<V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        List<V> result = Lists.newArrayList();
        for (Object k : vals.keySet()) {
            if (isSubKey(k))
                result.add( ((SubElementConfigKey<V>) k).extractValue(vals, exec) );
        }
        return Collections.unmodifiableList(result);
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
            String warning = "Discouraged undecorated setting of a collection to ListConfigKey "+this+": use ListModification.{set,add}. " +
            		"Defaulting to 'add'. Look at debug logging for call stack.";
            log.warn(warning);
            if (log.isDebugEnabled())
                log.debug("Trace for: "+warning, new Throwable("Trace for: "+warning));
            for (Object v: (Collection)value) 
                applyValueToMap(v, target);
            return null;
        } else {
            // just add to list, using anonymous key
            target.put(subKey(), value);
            return null;
        }
    }
    
    public interface ListModification<T> extends StructuredModification<ListConfigKey<T>>, List<T> {
    }
    
    public static class ListModifications extends StructuredModifications {
        /** when passed as a value to a ListConfigKey, causes each of these items to be added.
         * if you have just one, no need to wrap in a mod. */
        // to prevent confusion (e.g. if a list is passed) we require two objects here.
        public static final <T> ListModification<T> add(final T o1, final T o2, final T ...oo) {
            List<T> l = new ArrayList<T>();
            l.add(o1); l.add(o2);
            for (T o: oo) l.add(o);
            return new ListModificationBase<T>(l, false);
        }
        /** when passed as a value to a ListConfigKey, causes each of these items to be added */
        public static final <T> ListModification<T> addAll(final Collection<T> items) { 
            return new ListModificationBase<T>(items, false);
        }
        /** when passed as a value to a ListConfigKey, causes the items to be added as a single element in the list */
        public static final <T> ListModification<T> addItem(final T item) {
            return new ListModificationBase<T>(Collections.singletonList(item), false);
        }
        /** when passed as a value to a ListConfigKey, causes the list to be cleared and these items added */
        public static final <T> ListModification<T> set(final Collection<T> items) { 
            return new ListModificationBase<T>(items, true);
        }
    }

    @SuppressWarnings("serial")
    public static class ListModificationBase<T> extends ArrayList<T> implements ListModification<T> {
        private final boolean clearFirst;
        public ListModificationBase(Collection<T> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Object applyToKeyInMap(ListConfigKey<T> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            for (T o: this) target.put(key.subKey(), o);
            return null;
        }
    }
}
