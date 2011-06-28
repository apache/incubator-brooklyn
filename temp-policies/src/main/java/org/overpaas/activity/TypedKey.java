/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package org.overpaas.activity;

import java.lang.reflect.Type;

/**
 * Represents a hierarchical key that indexes into a nested map, and includes the type of the
 * value to be found there.
 * 
 * This is useful for json'ed nested maps. Given a map with strongly typed values in it, then 
 * json serialization will turn those values into maps (recursively reprsenting the fields of the
 * value). These values may be of arbitrary types. When reading the json, we therefore do not
 * know what type the value was supposed to be - the best we can do is to deserialize it as 
 * nested maps (with primitives/strings/collections at the leaves) to mirror the original json.
 * 
 * When a developer is given that data structure, they can ask for the strongly typed values that
 * they expect to be in the map. Only then can we deserialize the json to get the real value.
 * 
 * By using the TypedKey, one can define constants for the key+type so that code can be kept in 
 * sync for putting and getting the values. For example, MontereyLocationKeys.GEOSPATIAL_COORDINATES
 * defines a TypedKey that can be used to retrieve a value from the deserialized map (via the
 * NestedMapAccessor).
 * 
 * @author aled
 *
 * @param <T>
 */
public class TypedKey<T> {

    private final String path;
    private final String[] segments;
    private final EnhancedType<T> type;
    
    /**
     * @param path A slash-separated set of segments that make up the path.
     */
    public TypedKey(String path, EnhancedType<T> type) {
        this(path, split(path), type);
    }
    
    public TypedKey(String[] segments, EnhancedType<T> type) {
        this(merge(segments), segments, type);
    }
    
    public TypedKey(String[] segments, Class<T> type) {
        this(merge(segments), segments, new EnhancedType<T>(type));
    }
    
    private TypedKey(String path, String[] segments, EnhancedType<T> type) {
        this.path = merge(segments);
        this.segments = segments;
        this.type = type;
    }
    
    public TypedKey(String[] segments, Type type) {
        this(segments, new EnhancedType<T>(type));
    }

    public static String[] split(String keyPath) {
        assert keyPath!=null : "key identifier argument must be non-null";
        while (keyPath.startsWith("/")) keyPath = keyPath.substring(1);
        while (keyPath.endsWith("/")) keyPath = keyPath.substring(0, keyPath.length()-1);
        return keyPath.split("/");
    }

    public static String merge(String[] segments) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<segments.length; i++) {
            if (i != 0) sb.append("/");
            sb.append(segments[i]);
        }
        return sb.toString();
    }

    public String getPath() {
        return path;
    }
    
    public String[] getSegments() {
        String[] result = new String[segments.length];
        System.arraycopy(segments, 0, result, 0, segments.length);
        return result;
    }

    public EnhancedType<T> getType() {
        return type;
    }

//    public interface ImmutableAdapter<Source,Target> {
//        public Target toImmutable(Source s);
//    }
//    
//    public static abstract class KeyImmutableCollectionType<O,T extends Collection<O>> extends KeyType<T> implements ImmutableAdapter<Collection<O>,T> {
//        public KeyImmutableCollectionType(Class<T> type) {
//            super(type);
//        }
//        @SuppressWarnings("unchecked")
//        public T toImmutableUntyped(Object input) {
//            return toImmutable((Collection<O>) input); 
//        }
//    }
            
}
