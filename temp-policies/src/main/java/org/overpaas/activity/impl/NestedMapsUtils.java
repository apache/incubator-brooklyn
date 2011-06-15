/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package org.overpaas.activity.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilities for walking nested maps.
 * 
 * @author aled
 */
class NestedMapsUtils {
    
    // TODO Copied from Alex's ahgittin; has not had a thorough code review.
    
	private NestedMapsUtils() {}
	
	/** copies everything from source which does not override something in target into target, deeply recursing into maps */
	public static <T> void mergeDeepCopying(Map<String,T> target, Map<String,? extends T> source) {
		mergeDeepCopying(target, source, false);
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T> void mergeDeepCopying(Map<String,T> target, Map<String,? extends T> source, boolean override) {
		for (Map.Entry<String, ? extends T> e : source.entrySet()) {
			T tv = target.get(e.getKey());
			Object value = e.getValue();
			if (value instanceof Map) {
				if (tv==null || (override && !(tv instanceof Map))) {
					tv = (T) new LinkedHashMap();
					target.put(e.getKey(), tv);
				} else if (!(tv instanceof Map)) {
					//don't override
					continue;
				}
				mergeDeepCopying( (Map)tv, (Map)value );
			} else {
				if (tv!=null && !override) {
					//don't override
					continue;
				} else {
					target.put(e.getKey(), (T) value);
				}
			} 

			//TODO configurable collision behaviour?  merge collections?
		}
	}
	
	@SuppressWarnings({ "unchecked" })
	/** copies everything from source which does not override something in target into target, deeply recursing into maps */
	public static <T> Map<String,T> toUnmodifiableDeep(Map<String,? extends T> map) {
		Map<String,T> result = new LinkedHashMap<String, T>();
		for (Map.Entry<String, ? extends T> e : map.entrySet()) {
			Object value = e.getValue();
			if (value instanceof Map)
				value = toUnmodifiableDeep( (Map<String,? extends T>) value);
			result.put(e.getKey(), (T) value);
			//TODO collections?
//				else if (tv instanceof Collection) {
//					
//				}
		}
		return Collections.unmodifiableMap(result);
	}
}
