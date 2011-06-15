/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package org.overpaas.activity.impl;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.overpaas.activity.NestedMapAccessor;
import org.overpaas.activity.TypedKey;
import com.google.common.base.Preconditions;

/**
 * Accessor for getting/putting values into a nested map. Keys into the map are hierarchical,
 * which reflects maps within maps.
 * 
 * @author aled
 */
public class NestedMapAccessorImpl implements NestedMapAccessor {

    private final Map<String, Object> map;

    public NestedMapAccessorImpl(Map<String,Object> map) {
        this.map = map;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(TypedKey<T> key) {
        return (T) getRaw(key.getSegments());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String keySegments[], Class<T> type) {
        return (T) getRaw(keySegments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String keySegments[], Type type) {
        return (T) getRaw(keySegments);
    }

    /** returns default value if the key is not present; otherwise coerces to type of default value; null default value not allowed
     * (use the simpler get(key) method in that case) */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String[] keySegments, T defaultValue) {
        Preconditions.checkNotNull(defaultValue, "defaultValue");
        T result = (T) getRaw(keySegments);
        if (result!=null) return result;
        return defaultValue;
    }
    
    /** returns default value if the key is not present */
    @Override
    public <T> T getOrDefault(TypedKey<T> key, T defaultValue) {
        T result = get(key);
        if (result!=null) return result;
        return defaultValue;
    }

    /** 
     * Returns the entity stored at the given hierarchical key (without any type-coercion). If this 
     * has been json-deserialized, it will be a primitive, string, collection, or map.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Object getRaw(String[] keySegments) {
        // Validate that every part of the map is non-null non-empty
        for (String keySegment : keySegments) {
            if (keySegment == null || keySegment.isEmpty()) {
                throw new IllegalArgumentException("Key segments must all be non-empty: " + Arrays.asList(keySegments));
            }
        }

        // Find the nested leaf-map to put our value in
        Map m = map;
        for (int i = 0; i < keySegments.length - 1; i++) {
            if (m == null) {
                System.out.println("m is null");
            }
            Object nextMap = m.get(keySegments[i]);
            if (nextMap instanceof Map) {
                m = (Map) nextMap;
            } else {
                return null;
            }
        }

        return m.get(keySegments[keySegments.length - 1]);
    }
    
    public <T> void put(TypedKey<T> key, T val) {
        put(key.getSegments(), val);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object put(String[] keySegments, Object value) {
        // Validate that every part of the map is non-null non-empty
        for (String keySegment : keySegments) {
            if (keySegment == null || keySegment.isEmpty()) {
                throw new IllegalArgumentException("Key segments must all be non-empty: " + Arrays.asList(keySegments));
            }
        }

        // Find the nested leaf-map to put our value in
        Map m = map;
        for (int i = 0; i < keySegments.length - 1; i++) {
            Object nextMap = m.get(keySegments[i]);
            if (nextMap instanceof Map) {
                m = (Map) nextMap;
            } else {
                // if nextMap != null, then there was already a value against this key;
                // will overwrite the previous value and all of its children!
                nextMap = new LinkedHashMap<String, Object>();
                m.put(keySegments[i], nextMap);
                m = (Map) nextMap;
            }
        }

        String leafKey = keySegments[keySegments.length - 1];

        if (value instanceof Map && m.get(leafKey) instanceof Map) {
            NestedMapsUtils.mergeDeepCopying((Map) m.get(leafKey), (Map) value, true);
        } else {
            // If there was a previous value, will overwrite it and all its children!
            m.put(leafKey, value);
        }

        return this;
    }
    
    @Override public int hashCode() {
        return map.hashCode();
    }
    
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof NestedMapAccessorImpl)) {
            return false;
        }
        return map.equals(((NestedMapAccessorImpl)obj).map);
    }
    
    @Override public String toString() {
        return map.toString();
    }
}
