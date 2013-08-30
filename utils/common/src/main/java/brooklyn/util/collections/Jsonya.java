package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/** Jsonya = JSON-yet-another (tool) 
 * <p>
 * provides conveniences for working with maps and lists containing maps and lists,
 * and other datatypes too, easily convertible to json.
 * <p> 
 * see {@link JsonyaTest} for examples
 * 
 * @since 0.6.0
 **/
@Beta
public class Jsonya {

    private Jsonya() {}
    
    /** creates a {@link Navigator} backed by the given map (focussed at the root) */
    public static <T extends Map<?,?>> Navigator<T> of(T map) {
        return new Navigator<T>(map, MutableMap.class);
    }
    
    /** creates a {@link Navigator} backed by the map at the focus of the given navigator */
    public static <T extends Map<?,?>> Navigator<T> of(Navigator<T> navigator) {
        return new Navigator<T>(navigator.getFocusMap(), MutableMap.class);
    }
    
    /** creates a {@link Navigator} backed by a newly created map;
     * the map can be accessed by {@link Navigator#getMap()} */
    public static Navigator<MutableMap<Object,Object>> newInstance() {
        return new Navigator<MutableMap<Object,Object>>(new MutableMap<Object,Object>(), MutableMap.class);
    }
    /** convenience for {@link Navigator#at(Object, Object...)} on a {@link #newInstance()} */
    public static Navigator<MutableMap<Object,Object>> at(Object ...pathSegments) {
        return newInstance().atArray(pathSegments);
    }
    
    @SuppressWarnings({"rawtypes","unchecked"})
    public static class Navigator<T extends Map<?,?>> {

        protected final Object root;
        protected final Class<? extends Map> mapType;
        protected Object focus;
        protected Function<Object,Void> creationInPreviousFocus;

        public Navigator(Object backingStore, Class<? extends Map> mapType) {
            this.root = Preconditions.checkNotNull(backingStore);
            this.focus = backingStore;
            this.mapType = mapType;
        }
        
        // -------------- access
        
        /** returns the object at the focus, or null if none */
        public Object get() {
            return focus;
        }
        
        /** returns the object at the focus, casted to the given type, null if none */
        public <V> V get(Class<V> type) {
            return (V)focus;
        }
        
        public Object get(Object pathSegment, Object ...furtherPathSegments) {
            at(pathSegment, furtherPathSegments);
            return get();
        }
        
        public Navigator<T> root() {
            focus = root;
            return this;
        }

        /** returns the object at the root */
        public Object getRoot() {
            return root;
        }
        
        /** returns the {@link Map} at the root, throwing if root is not a map */
        public T getRootMap() {
            return (T) root;
        }

        /** returns a {@link Map} at the given focus, creating if needed (so never null),
         * throwing if it exists already and is not a map */
        public T getFocusMap() {
            map();
            return (T)focus;
        }

        // ------------- navigation (map mainly)

        /** returns the navigator focussed at the indicated key sequence in the given map */
        public Navigator<T> at(Object pathSegment, Object ...furtherPathSegments) {
            down(pathSegment);
            return atArray(furtherPathSegments);
        }
        public Navigator<T> atArray(Object[] furtherPathSegments) {
            for (Object p: furtherPathSegments)
                down(p);
            return this;
        }
        
        /** ensures the given focus is a map, creating if needed (and creating inside the list if it is in a list) */
        public Navigator<T> map() {
            if (focus==null) {
                focus = newMap();
                creationInPreviousFocus.apply(focus);
            }
            if (focus instanceof List) {
                Map m = newMap();
                ((List)focus).add(m);
                focus = m;
                return this;
            }
            if (!(focus instanceof Map))
                throw new IllegalStateException("focus here is "+focus+"; expected a map");
            return this;
        }

        /** puts the given key-value pair at the current focus (or multiple such), 
         *  creating a map if needed, replacing any values stored against keys supplied here;
         *  if you wish to merge deep maps, see {@link #add(Object, Object...)} */
        public Navigator<T> put(Object k1, Object v1, Object ...kvOthers) {
            map();
            putInternal((Map)focus, k1, v1, kvOthers);
            return this;
        }
        
        protected static void putInternal(Map target, Object k1, Object v1, Object ...kvOthers) {
            assert (kvOthers.length % 2) == 0 : "even number of arguments required for put";
            target.put(k1, v1);
            for (int i=0; i<kvOthers.length; ) {
                target.put(kvOthers[i++], kvOthers[i++]);    
            }
        }

        /** as {@link #put(Object, Object, Object...)} for the kv-pairs in the given map */
        public Navigator<T> put(Map map) {
            map();
            ((Map)focus).putAll(map);
            return this;
        }

        protected Map newMap() {
            try {
                return mapType.newInstance();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        /** utility for {@link #at(Object, Object...)}, taking one argument at a time */
        protected Navigator<T> down(final Object pathSegment) {
            if (focus instanceof List) {
                return downList(pathSegment);
            }
            if ((focus instanceof Map) || focus==null) {
                return downMap(pathSegment);
            }
            throw new IllegalStateException("focus here is "+focus+"; cannot descend to '"+pathSegment+"'");
        }

        protected Navigator<T> downMap(final Object pathSegment) {
            final Map givenParentMap = (Map)focus;
            if (givenParentMap!=null) {
                creationInPreviousFocus = null;
                focus = givenParentMap.get(pathSegment);
            }
            if (focus==null) {
                final Function<Object, Void> previousCreation = creationInPreviousFocus;
                creationInPreviousFocus = new Function<Object, Void>() {
                    public Void apply(Object input) {
                        creationInPreviousFocus = null;
                        Map parentMap = givenParentMap;
                        if (parentMap==null) {
                            parentMap = newMap();
                            previousCreation.apply(parentMap);
                        }
                        parentMap.put(pathSegment, input);
                        return null;
                    }
                };
            }
            return this;
        }

        protected Navigator<T> downList(final Object pathSegment) {
            if (!(pathSegment instanceof Integer))
                throw new IllegalStateException("focus here is a list ("+focus+"); cannot descend to '"+pathSegment+"'");
            final List givenParentList = (List)focus;
            // previous focus always non-null
            creationInPreviousFocus = null;
            focus = givenParentList.get((Integer)pathSegment);
            if (focus==null) {
                // don't need to worry about creation here; we don't create list entries simply by navigating
                // TODO a nicer architecture would create a new object with focus for each traversal
                // in that case we could create, filling other positions with null; but is there a need?
                creationInPreviousFocus = new Function<Object, Void>() {
                    public Void apply(Object input) {
                        throw new IllegalStateException("cannot create "+input+" here because we are at a non-existent position in a list");
                    }
                };
            }
            return this;
        }

        // ------------- navigation (list mainly)

        /** ensures the given focus is a list */
        public Navigator<T> list() {
            if (focus==null) {
                focus = newList();
                creationInPreviousFocus.apply(focus);
            }
            if (!(focus instanceof List))
                throw new IllegalStateException("focus here is "+focus+"; expected a list");
            return this;
        }

        protected List newList() {
            return new ArrayList();
        }
        
        /** adds the given items to the focus, whether a list or a map,
         * creating the focus as a map if it doesn't already exist.
         * to add items to a list which might not exist, precede by a call to {@link #list()}.
         * <p>
         * when adding items to a list, iterable and array arguments are flattened because 
         * that makes the most sense when working with deep maps (adding one map to another where both contain lists, for example); 
         * to prevent flattening use {@link #addUnflattened(Object, Object...)} 
         * <p>
         * when adding to a map, arguments will be treated as things to put into the map,
         * accepting either multiple arguments, as key1, value1, key2, value2, ...
         * (and must be an event number); or a single argument which must be a map,
         * in which case the value for each key in the supplied map is added to any existing value against that key in the target map
         * (in other words, it will do a "deep put", where nested maps are effectively merged)
         * <p>
         * this implementation will currently throw if you attempt to add a non-map to anything present which is not a list;
         * auto-conversion to a list may be added in a future version
         * */
        public Navigator<T> add(Object o1, Object ...others) {
            if (focus==null) map();
            addInternal(focus, focus, o1, others);
            return this;
        }

        /** adds the given arguments to a list at this point (will not descend into maps, and will not flatten lists) */
        public Navigator<T> addUnflattened(Object o1, Object ...others) {
            ((Collection)focus).add(o1);
            for (Object oi: others) ((Collection)focus).add(oi);
            return this;
        }
        
        protected static void addInternal(Object initialFocus, Object currentFocus, Object o1, Object ...others) {
            if (currentFocus instanceof Map) {
                Map target = (Map)currentFocus;
                Map source;
                if (others.length==0) {
                    // add as a map
                    if (o1==null)
                        // ignore if null
                        return ;
                    if (!(o1 instanceof Map))
                        throw new IllegalStateException("cannot add: focus here is "+currentFocus+" (in "+initialFocus+"); expected a collection, or a map (with a map being added, not "+o1+")");
                    source = (Map)o1;
                } else {
                    // build a source map from the arguments as key-value pairs
                    if ((others.length % 2)==0)
                        throw new IllegalArgumentException("cannot add an odd number of arguments to a map" +
                        		" ("+o1+" then "+Arrays.toString(others)+" in "+currentFocus+" in "+initialFocus+")");
                    source = MutableMap.of(o1, others[0]);
                    for (int i=1; i<others.length; )
                        source.put(others[i++], others[i++]);
                }
                // and add the source map to the target
                for (Object entry : source.entrySet()) {
                    Object key = ((Map.Entry)entry).getKey();
                    Object sv = ((Map.Entry)entry).getValue();
                    Object tv = target.get(key);
                    if (!target.containsKey(key)) {
                        target.put(key, sv);
                    } else {
                        addInternal(initialFocus, tv, sv);
                    }                        
                }
                return;
            }
            // lists are easy to add to, but remember we have to flatten
            if (!(currentFocus instanceof Collection))
                // TODO a nicer architecture might replace the current target with a list (also above where single non-map argument is supplied)
                throw new IllegalStateException("cannot add: focus here is "+currentFocus+"; expected a collection");
            addFlattened((Collection)currentFocus, o1);
            for (Object oi: others) addFlattened((Collection)currentFocus, oi); 
        }

        protected static void addFlattened(Collection target, Object item) {
            if (item instanceof Iterable) {
                for (Object i: (Iterable)item)
                    addFlattened(target, i);
                return;
            }
            if (item.getClass().isArray()) {
                for (Object i: ((Object[])item))
                    addFlattened(target, i);
                return;
            }
            // nothing to flatten
            target.add(item);
        }

    }
    
}
