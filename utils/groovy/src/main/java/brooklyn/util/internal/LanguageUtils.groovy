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
package brooklyn.util.internal

import java.lang.reflect.Field
import java.lang.reflect.Method;
import java.lang.reflect.Modifier
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong

import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Identifiers

import com.google.common.annotations.Beta
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Useful Groovy utility methods.
 * 
 * @deprecated since 0.5; requires thorough review for what will be kept.
 *             e.g. consider instead using guava's {@link com.google.common.collect.Multimap} instead of addToMapOfSets etc
 */
@Deprecated
@Beta
public class LanguageUtils {
    // For unique identifiers
    private static final AtomicLong seed = new AtomicLong(0L)

    public static <T> T getRequiredField(String name, Map<?,?> m) {
        if (!m.containsKey(name))
            throw new IllegalArgumentException("a parameter '"+name+"' was required in the argument to this function")
        m.get name
    }

    public static <T> T getOptionalField(String name, Map<?,?> m, T defaultValue=null) {
        m.get(name) ?: defaultValue
    }

    public static <T> T getPropertySafe(Object target, String name, T defaultValue=null) {
        target.hasProperty(name)?.getProperty(target) ?: defaultValue
    }

    //TODO find with annotation

    public static byte[] serialize(Object orig) {
        if (orig == null) return null;

        // Write the object out to a byte array
        ByteArrayOutputStream fbos = []
        ObjectOutputStream out = new ObjectOutputStream(fbos);
        out.writeObject(orig);
        out.flush();
        out.close();
        return fbos.toByteArray();
    }

    public static <T> T deserialize(byte[] bytes, ClassLoader classLoader) {
        if (bytes == null) return null;

        ObjectInputStream ins =
                //new ObjectInputStreamWithLoader(new FastByteArrayInputStream(bytes, bytes.length), classLoader);
                new ObjectInputStream(new ByteArrayInputStream(bytes));
        (T) ins.readObject();
    }

    /**
     * @deprecated use Identifiers.makeRandomId(8)
     */
    @Deprecated
    public static String newUid() { Identifiers.makeRandomId(8) }

    public static Map setFieldsFromMap(Object target, Map fieldValues) {
        Map unused = [:]
        fieldValues.each {
            //            println "looking for "+it.key+" in "+target+": "+target.metaClass.hasProperty(it.key)
            target.hasProperty(it.key) ? target.(it.key) = it.value : unused << it
        }
        unused
    }

    /**
     * Adds the given value to a collection in the map under the key.
     * 
     * A collection (as {@link LinkedHashMap}) will be created if necessary,
     * synchronized on map for map access/change and set for addition there
     *
     * @return the updated set (instance, not copy)
     * 
     * @deprecated since 0.5; use {@link HashMultimap}, and {@link Multimaps#synchronizedSetMultimap(com.google.common.collect.SetMultimap)}
     */
    @Deprecated
    public static <K,V> Set<V> addToMapOfSets(Map<K,Set<V>> map, K key, V valueInCollection) {
        Set<V> coll;
        synchronized (map) {
            coll = map.get(key)
            if (coll==null) {
                coll = new LinkedHashSet<V>()
                map.put(key, coll)
            }
            if (coll.isEmpty()) {
                synchronized (coll) {
                    coll.add(valueInCollection)
                }
                //if collection was empty then add to the collection while holding the map lock, to prevent removal
                return coll
            }
        }
        synchronized (coll) {
            if (!coll.isEmpty()) {
                coll.add(valueInCollection)
                return coll;
            }
        }
        //if was empty, recurse, because someone else might be removing the collection
        return addToMapOfSets(map, key, valueInCollection);
    }

    /**
     * as {@link #addToMapOfSets(Map, Object, Object)} but for {@link ArrayList}
     * 
     * @deprecated since 0.5; use {@link ArrayListMultimap}, and {@link Multimaps#synchronizedListMultimap(com.google.common.collect.ListMultimap)}
     */
    @Deprecated
    public static <K,V> List<V> addToMapOfLists(Map<K,List<V>> map, K key, V valueInCollection) {
        List<V> coll;
        synchronized (map) {
            coll = map.get(key)
            if (coll==null) {
                coll = new ArrayList<V>()
                map.put(key, coll)
            }
            if (coll.isEmpty()) {
                synchronized (coll) {
                    coll.add(valueInCollection)
                }
                //if collection was empty then add to the collection while holding the map lock, to prevent removal
                return coll
            }
        }
        synchronized (coll) {
            if (!coll.isEmpty()) {
                coll.add(valueInCollection)
                return coll;
            }
        }
        //if was empty, recurse, because someone else might be removing the collection
        return addToMapOfLists(map, key, valueInCollection);
    }

    /**
     * Removes the given value from a collection in the map under the key.
     *
     * @return the updated set (instance, not copy)
     * 
     * @deprecated since 0.5; use {@link ArrayListMultimap} or {@link HashMultimap}, and {@link Multimaps#synchronizedListMultimap(com.google.common.collect.ListMultimap)} etc
     */
    @Deprecated
    public static <K,V> boolean removeFromMapOfCollections(Map<K,? extends Collection<V>> map, K key, V valueInCollection) {
        Collection<V> coll;
        synchronized (map) {
            coll = map.get(key)
            if (coll==null) return false;
        }
        boolean result;
        synchronized (coll) {
            result = coll.remove(valueInCollection)
        }
        if (coll.isEmpty()) {
            synchronized (map) {
                synchronized (coll) {
                    if (coll.isEmpty()) {
                        //only remove from the map if no one is adding to the collection or to the map, and the collection is still in the map
                        if (map.get(key)==coll) {
                            map.remove(key)
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Visits all fields of a given object, recursively.
     *
     * For collections, arrays, and maps it visits the items within, passing null for keys where it isn't a map.
     */
    public static void visitFields(Object o, FieldVisitor fv, Collection<Object> objectsToSkip=([] as Set)) {
        if (o == null || objectsToSkip.contains(o)) return
        objectsToSkip << o
        if (o in String) return
        if (o in Map) {
            o.each { key, value ->
                fv.visit(o, key.toString(), value)
                visitFields(value, fv, objectsToSkip)
            }
        } else if ((o in Collection) || (o.getClass().isArray())) {
            o.each {
                entry ->
                fv.visit(o, null, entry)
                visitFields(entry, fv, objectsToSkip)
            }
        } else {
            o.getClass().getDeclaredFields().each {
                Field field ->
                if ((field.getModifiers() & Modifier.STATIC) || field.isSynthetic()) return;  //skip static
                field.setAccessible true
                def v = field.get(o);
                fv.visit(o, field.name, v)
                visitFields(v, fv, objectsToSkip)
            }
        }
    }

    public interface FieldVisitor {
        /** Invoked by visitFields; fieldName will be null for collections */
        public void visit(Object parent, String fieldName, Object value)
    }

    /**
     * Iterates through two collections simultaneously, passing both args to code.
     * 
     * <pre>
     * a = ['a','b']; b=[1,2];
     * assert ['a1','b2'] == forboth(a,b) { x,y -> x+y }
     * </pre>
     */
    public static Collection forBoth(Collection l1, Collection l2, Closure code) {
        def result=[]
        l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i]) ) }
        result
    }

    public static Collection forBothWithIndex(Collection l1, Collection l2, Closure code) {
        def result=[]
        l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i], i) ) }
        result
    }

    public static Collection forBoth(Object[] l1, Object[] l2, Closure code) {
        def result=[]
        l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i]) ) }
        result
    }

    public static Collection forBothWithIndex(Object[] l1, Object[] l2, Closure code) {
        def result=[]
        l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i], i) ) }
        result
    }

    /** return value used to indicate that there is no such field */
    public static final Object NO_SUCH_FIELD = new Object();

    /**
     * Default field getter.
     *
     * Delegates to {@code object[field]} (which will invoke a getter if one exists, in groovy),
     * unless field starts with {@literal @} in which case it looks up the actual java field (bypassing getter).
     * <p>
     * Can be extended as needed when passed to {@link #equals(Object, Object, Class, String[])}
     */
    public static final Closure DEFAULT_FIELD_GETTER = { Object object, Object field ->
        try {
            if ((field in String) && field.startsWith("@")) {
                return object.@"${field.substring(1)}"
            }
            return object[field]
        } catch (Exception e) {
            return NO_SUCH_FIELD
        }
    }

    /**
     * Checks equality of o1 and o2 with respect to the named fields, optionally enforcing a common superclass
     * and using a custom field-getter.
     *
     * Other types can be supplied if they are supported by {@code object[field]} (what the {@link #DEFAULT_FIELD_GETTER} does)
     * or if the {@literal optionalGetter} handles it. Note that {@code object[field]} causes invocation of {@code object.getAt(field)}
     * (which can be provided on the object for non-strings - this is preferred to an optionalGetter, generally)
     * looking for {@code object.getXxx()}, where field is a string {@literal xxx}, then {@code object.xxx}.
     * <p>
     * One exception is that field names which start with {@literal @} get the field directly according to {@link #DEFAULT_FIELD_GETTER},
     * but use with care on private fields, as they must be on the object and not a superclass, and with groovy properties
     * (formerly known as package-private, i.e. with no access modifiers) because they become private fields.
     * <p>
     * For example
     * <pre>
     * public class Foo {
     *   Object bar;
     *   public boolean equals(Object other) { LangaugeUtils.equals(this, other, Foo.class, ["bar"]); }
     *   public int hashCode() { LangaugeUtils.hashCode(this, ["bar"]); }
     * }
     * </pre>
     *
     * @param o1 one object to compare
     * @param o2 other object to compare
     * @param optionalCommonSuperClass if supplied, returns false unless both objects are instances of the given type;
     *     (if not supplied it effectively does duck typing, returning false if any field is not present)
     * @param optionalGetter if supplied, a closure which takes (object, field) and returns the value of field on object;
     *  should return static {@link #NO_SUCH_FIELD} if none found;
     *  recommended to delegate to {@link #DEFAULT_FIELD_GETTER} at least for strings (or for anything)
     * @param fields typically a list of strings being names of fields on the class to compare
     * @return true if the two objects are equal in all indicated fields, and conform to the optionalCommonSuperClass if supplied
     */
    public static boolean equals(Object o1, Object o2, Class<?> optionalCommonSuperClass=null, Closure optionalGetter=null, Iterable<Object> fieldNames) {
        if (o1==null) return o2==null;
        if (o2==null) return false;
        if (optionalCommonSuperClass) {
            if (!(o1 in optionalCommonSuperClass) || !(o2 in optionalCommonSuperClass)) return false
        }
        Closure get = optionalGetter ?: DEFAULT_FIELD_GETTER
        for (it in fieldNames) {
            def v1 = get.call(o1, it)
            if (v1==NO_SUCH_FIELD) return false
            if (v1!=get.call(o2, it)) return false
        }
        return true
    }

    public static boolean equals(Object o1, Object o2, Class<?> optionalCommonSuperClass=null, Closure optionalGetter=null, Object[] fieldNames) {
        return equals(o1, o2, optionalCommonSuperClass, optionalGetter, Arrays.asList(fieldNames) )
    }

    /**
     * Generates a hashcode for an object.
     * 
     * Similar to {@link com.google.common.base.Objects#hashCode()} but taking field <em>names</em> and an optional getter,
     * with the same rich groovy semantics as described in {@link #equals(Object, Object, Class)}.
     */
    public static int hashCode(Object o, Closure optionalGetter=null, Collection<Object> fieldNames) {
        if (o==null) return 0;
        Closure get = optionalGetter ?: DEFAULT_FIELD_GETTER
        int result = 1;
        for (it in fieldNames) {
            def v1 = get.call(o, it)
            if (v1==NO_SUCH_FIELD)
                throw new NoSuchFieldError("Cannot access $it on "+o.getClass());
            result = 31 * result + (it == null ? 0 : it.hashCode());
        }
        result
    }

    public static int hashCode(Object o, Closure optionalGetter=null, Object[] fieldNames) {
        hashCode(o, optionalGetter, Arrays.asList(fieldNames))
    }
    
    /** Default String representation is simplified name of class, together with selected fields. */
    public static String toString(Object o, Closure optionalGetter=null, Collection<? extends CharSequence> fieldNames) {
        if (o==null) return null;
        Closure get = optionalGetter ?: DEFAULT_FIELD_GETTER
        
        StringBuilder result = new StringBuilder();
        result.append(o.getClass().getSimpleName());
        if (result.length() == 0) result.append(o.getClass().getName());
        List<Object> fieldVals = fieldNames.collect {
                Object v = get.call(o, it);
                return (v != null) ? it+"="+v : null;
        }
        result.append("[").append(Joiner.on(",").skipNulls().join(fieldVals)).append("]");
        return result.toString();
    }
}
