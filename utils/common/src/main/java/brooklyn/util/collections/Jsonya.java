/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.annotation.Nonnull;

import brooklyn.util.guava.Maybe;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.primitives.Primitives;

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

    /** as {@link #newInstance()} but using the given translator to massage objects inserted into the Jsonya structure */
    public static Navigator<MutableMap<Object,Object>> newInstanceTranslating(Function<Object,Object> translator) {
        return newInstance().useTranslator(translator);
    }

    /** as {@link #newInstanceTranslating(Function)} using an identity function
     * (functionally equivalent to {@link #newInstance()} but explicit about it */
    public static Navigator<MutableMap<Object,Object>> newInstanceLiteral() {
        return newInstanceTranslating(Functions.identity());
    }

    /** as {@link #newInstanceTranslating(Function)} using a function which only supports JSON primitives:
     * maps and collections are traversed, strings and primitives are inserted, and everything else has toString applied.
     * see {@link JsonPrimitiveDeepTranslator} */
    public static Navigator<MutableMap<Object,Object>> newInstancePrimitive() {
        return newInstanceTranslating(new JsonPrimitiveDeepTranslator());
    }
    
    /** convenience for converting an object x to something which consists only of json primitives, doing
     * {@link #toString()} on anything which is not recognised. see {@link JsonPrimitiveDeepTranslator} */
    public static Object convertToJsonPrimitive(Object x) {
        if (x==null) return null;
        if (x instanceof Map) return newInstancePrimitive().put((Map<?,?>)x).getRootMap();
        return newInstancePrimitive().put("data", x).getRootMap().get("data");
    }

    /** tells whether {@link #convertToJsonPrimitive(Object)} returns an object which is identical to
     * the equivalent literal json structure. this is typically equivalent to saying serializing to json then
     * deserializing will produce something where the result is equal to the input,
     * modulo a few edge cases such as longs becoming ints.
     * note that the converse (input equal to output) may not be the case,
     * e.g. if the input contains special subclasses of collections of maps who care about type preservation. */
    public static boolean isJsonPrimitiveCompatible(Object x) {
        if (x==null) return true;
        return convertToJsonPrimitive(x).equals(x);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public static class Navigator<T extends Map<?,?>> {

        protected final Object root;
        protected final Class<? extends Map> mapType;
        protected Object focus;
        protected Stack<Object> focusStack = new Stack<Object>();
        protected Function<Object,Void> creationInPreviousFocus;
        protected Function<Object,Object> translator;

        public Navigator(Object backingStore, Class<? extends Map> mapType) {
            this.root = Preconditions.checkNotNull(backingStore);
            this.focus = backingStore;
            this.mapType = mapType;
        }
        
        // -------------- access and configuration
        
        /** returns the object at the focus, or null if none */
        public Object get() {
            return focus;
        }

        /** as {@link #get()} but always wrapped in a {@link Maybe}, absent if null */
        public @Nonnull Maybe<Object> getMaybe() {
            return Maybe.fromNullable(focus);
        }
        
        /** returns the object at the focus, casted to the given type, null if none
         * @throws ClassCastException if object exists here but of the wrong type  */
        public <V> V get(Class<V> type) {
            return (V)focus;
        }

        /** as {@link #get(Class)} but always wrapped in a {@link Maybe}, absent if null
         * @throws ClassCastException if object exists here but of the wrong type  */
        public @Nonnull <V> Maybe<V> getMaybe(Class<V> type) {
            return Maybe.fromNullable(get(type));
        }

        /** gets the object at the indicated path from the current focus
         * (without changing the path to that focus; use {@link #at(Object, Object...)} to change focus) */
        // Jun 2014, semantics changed so that focus does not change, which is more natural
        public Object get(Object pathSegment, Object ...furtherPathSegments) {
            push();
            at(pathSegment, furtherPathSegments);
            Object result = get();
            pop();
            return result;
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
        
        /** as {@link #getFocusMap()} but always wrapped in a {@link Maybe}, absent if null
         * @throws ClassCastException if object exists here but of the wrong type  */
        public @Nonnull Maybe<T> getFocusMapMaybe() {
            return Maybe.fromNullable(getFocusMap());
        }

        /** specifies a translator function to use when new data is added;
         * by default everything is added as a literal (ie {@link Functions#identity()}), 
         * but if you want to do translation on the way in,
         * set a translation function
         * <p>
         * note that translation should be idempotent as implementation may apply it multiple times in certain cases
         */
        public Navigator<T> useTranslator(Function<Object,Object> translator) {
            this.translator = translator;
            return this;
        }
        
        protected Object translate(Object x) {
            if (translator==null) return x;
            return translator.apply(x);
        }

        protected Object translateKey(Object x) {
            if (translator==null) return x;
            // this could return the toString to make it strict json
            // but json libraries seem to do that so not strictly necessary
            return translator.apply(x);
        }

        // ------------- navigation (map mainly)

        /** pushes the current focus to a stack, so that this location will be restored on the corresponding {@link #pop()} */
        public Navigator<T> push() {
            focusStack.push(focus);
            return this;
        }
        
        /** pops the most recently pushed focus, so that it returns to the last location {@link #push()}ed */
        public Navigator<T> pop() {
            focus = focusStack.pop();
            return this;
        }
        
        /** returns the navigator moved to focus at the indicated key sequence in the given map */
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
                ((List)focus).add(translate(m));
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
        
        public Navigator<T> putIfNotNull(Object k1, Object v1) {
            if (v1!=null) {
                map();
                putInternal((Map)focus, k1, v1);
            }
            return this;
        }
        
        protected void putInternal(Map target, Object k1, Object v1, Object ...kvOthers) {
            assert (kvOthers.length % 2) == 0 : "even number of arguments required for put";
            target.put(translateKey(k1), translate(v1));
            for (int i=0; i<kvOthers.length; ) {
                target.put(translateKey(kvOthers[i++]), translate(kvOthers[i++]));    
            }
        }

        /** as {@link #put(Object, Object, Object...)} for the kv-pairs in the given map; ignores null for convenience */
        public Navigator<T> put(Map map) {
            map();
            if (map==null) return this;
            ((Map)focus).putAll((Map)translate(map));
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

        protected Navigator<T> downMap(Object pathSegmentO) {
            final Object pathSegment = translateKey(pathSegmentO);
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
                        parentMap.put(pathSegment, translate(input));
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
            ((Collection)focus).add(translate(o1));
            for (Object oi: others) ((Collection)focus).add(translate(oi));
            return this;
        }
        
        protected void addInternal(Object initialFocus, Object currentFocus, Object o1, Object ...others) {
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
                    source = (Map)translate(o1);
                } else {
                    // build a source map from the arguments as key-value pairs
                    if ((others.length % 2)==0)
                        throw new IllegalArgumentException("cannot add an odd number of arguments to a map" +
                        		" ("+o1+" then "+Arrays.toString(others)+" in "+currentFocus+" in "+initialFocus+")");
                    source = MutableMap.of(translateKey(o1), translate(others[0]));
                    for (int i=1; i<others.length; )
                        source.put(translateKey(others[i++]), translate(others[i++]));
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

        protected void addFlattened(Collection target, Object item) {
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
            target.add(translate(item));
        }
        
        /** Returns JSON serialized output for given focus in the given jsonya;
         * applies a naive toString for specialized types */
        @Override
        public String toString() {
            return render(get());
        }
    }

    public static String render(Object focus) {
        if (focus instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Object entry: ((Map<?,?>)focus).entrySet()) {
                if (!first) sb.append(",");
                else first = false;
                sb.append(" ");
                sb.append( render(((Map.Entry<?,?>)entry).getKey()) );
                sb.append(": ");
                sb.append( render(((Map.Entry<?,?>)entry).getValue()) );
            }
            sb.append(" }");
            return sb.toString();
        }
        if (focus instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object entry: (Collection<?>)focus) {
                if (!first) sb.append(",");
                else first = false;
                sb.append( render(entry) );
            }
            sb.append(" ]");
            return sb.toString();
        }
        if (focus instanceof String) {
            return JavaStringEscapes.wrapJavaString((String)focus);
        }
        if (focus == null || focus instanceof Number || focus instanceof Boolean)
            return ""+focus;
        
        return render(""+focus);
    }

    /** Converts an object to one which uses standard JSON objects where possible
     * (strings, numbers, booleans, maps, lists), and uses toString elsewhere */
    public static class JsonPrimitiveDeepTranslator implements Function<Object,Object> {
        public static JsonPrimitiveDeepTranslator INSTANCE = new JsonPrimitiveDeepTranslator();
        
        /** No need to instantiate except when subclassing. Use static {@link #INSTANCE}. */
        protected JsonPrimitiveDeepTranslator() {}
        
        @Override
        public Object apply(Object input) {
            return apply(input, new HashSet<Object>());
        }
        
        protected Object apply(Object input, Set<Object> stack) {
            if (input==null) return applyNull(stack);
            
            if (isPrimitiveOrBoxer(input.getClass()))
                return applyPrimitiveOrBoxer(input, stack);
            
            if (input instanceof String)
                return applyString((String)input, stack);
            
            stack = new HashSet<Object>(stack);
            if (!stack.add(input))
                // fail if object is self-recursive; don't even try toString as that is dangerous
                // (extra measure of safety, since maps and lists generally fail elsewhere with recursive entries, 
                // eg in hashcode or toString)
                return "[REF_ANCESTOR:"+stack.getClass()+"]";

            if (input instanceof Collection<?>)
                return applyCollection( (Collection<?>)input, stack );
            
            if (input instanceof Map<?,?>)
                return applyMap( (Map<?,?>)input, stack );

            return applyOther(input, stack);
        }

        protected Object applyNull(Set<Object> stack) {
            return null;
        }

        protected Object applyPrimitiveOrBoxer(Object input, Set<Object> stack) {
            return input;
        }

        protected Object applyString(String input, Set<Object> stack) {
            return input.toString();
        }

        protected Object applyCollection(Collection<?> input, Set<Object> stack) {
            MutableList<Object> result = MutableList.of();
            
            for (Object xi: input)
                result.add(apply(xi, stack));

            return result;
        }

        protected Object applyMap(Map<?, ?> input, Set<Object> stack) {
            MutableMap<Object, Object> result = MutableMap.of();
            
            for (Map.Entry<?,?> xi: input.entrySet())
                result.put(apply(xi.getKey(), stack), apply(xi.getValue(), stack));

            return result;
        }

        protected Object applyOther(Object input, Set<Object> stack) {
            return input.toString();
        }        

        public static boolean isPrimitiveOrBoxer(Class<?> type) {
            return Primitives.allPrimitiveTypes().contains(type) || Primitives.allWrapperTypes().contains(type);
        }
    }

}
