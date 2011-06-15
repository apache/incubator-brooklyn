/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package org.overpaas.activity;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * 
 * @author aled
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class EnhancedType<T> {
    
    // TODO Copied from Alex's ahgittin. Is this class using Type and Class<?> in the correct way
    // for gson deserialization?
    
    public static EnhancedType<String> STRING = new EnhancedType<String>(String.class);
    public static EnhancedType<Double> NUMBER = new EnhancedType<Double>(Double.class);
    public static EnhancedType<Boolean> BOOLEAN = new EnhancedType<Boolean>(Boolean.class);
    //we use this <? extends X> to "enforce" immutability (at least weakly)
    //TODO pass Type impls
    public static EnhancedType<Collection<? extends String>> COLLECTION_STRINGS = new EnhancedType(Collection.class);
    public static EnhancedType<Collection<? extends Object>> COLLECTION_OBJECTS = new EnhancedType(Collection.class);
    public static EnhancedType<Map<String,? extends String>> MAP_STRING_STRING = new EnhancedType(Map.class);
    public static EnhancedType<Map<String,? extends Object>> MAP_STRING_OBJECT = new EnhancedType(Map.class);
    
	private final Class<T> javaClass;
	private final Type javaType;
	
	public EnhancedType(Class<T> javaClass) {
		this(javaClass, javaClass);
	}
	
    public EnhancedType(Type typeWithGenericInformation) {
        this.javaClass = null;
        this.javaType = typeWithGenericInformation;
    }
    
	public EnhancedType(Class<T> javaClass, Type typeWithGenericInformation) {
		this.javaClass = javaClass;
		this.javaType = typeWithGenericInformation;
	}
	
	public Class<T> asJavaClass() {
		return javaClass;
	}
	
	public Type asJavaType() {
		return javaType;
	}
	
	//could move toward this format to support strong immutability
//		public static KeyImmutableCollectionType<String,List<String>> IMMUTABLE_COLLECTION_OF_STRINGS = new KeyImmutableCollectionType<String,List<String>>( (Class) List.class) {
//			public List<String> toImmutable(Collection<String> input) {
//				return Collections.unmodifiableList(new ArrayList<String>(input));
//			}
//		};
//		public static KeyImmutableCollectionType<Object,List<Object>> IMMUTABLE_COLLECTION_OF_OBJECTS = new KeyImmutableCollectionType<Object,List<Object>>( (Class) List.class) {
//			public List<Object> toImmutable(Collection<Object> input) {
//				return Collections.unmodifiableList(new ArrayList<Object>(input));
//			}
//		};
	
}
