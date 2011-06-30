/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package org.overpaas.activity;

import java.lang.reflect.Type;

/**
 * Accessor for getting/putting values into a nested map. Keys into the map are hierarchical,
 * which reflects maps within maps.
 * 
 * @author aled
 */
public interface NestedMapAccessor {

    public <T> T get(TypedKey<T> key);
    
    public <T> T get(String keySegments[], Class<T> type);

    public <T> T get(String keySegments[], Type type);

    /** returns default value if the key is not present; otherwise coerces to type of default value; null default value not allowed
     * (use the simpler get(key) method in that case) */
    public <T> T getOrDefault(String[] keySegments, T defaultValue);
    
    /** returns default value if the key is not present */
    public <T> T getOrDefault(TypedKey<T> key, T defaultValue);

    /** 
     * Returns the entity stored at the given hierarchical key (without any type-coercion). If this 
     * has been json-deserialized, it will be a primitive, string, collection, or map.
     */
    public Object getRaw(String[] keySegments);
}
