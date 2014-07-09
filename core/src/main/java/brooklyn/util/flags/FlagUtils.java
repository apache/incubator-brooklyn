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
package brooklyn.util.flags;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.trait.Configurable;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/** class to help transfer values passed as named arguments to other well-known variables/fields/objects;
 * see the test case for example usage */
public class FlagUtils {

    public static final Logger log = LoggerFactory.getLogger(FlagUtils.class);
    
    private FlagUtils() {}
    
    /** see {@link #setFieldsFromFlags(Object o, ConfigBag)} */
    public static Map<?, ?> setPublicFieldsFromFlags(Map<?, ?> flags, Object o) {
        return setFieldsFromFlagsInternal(o, Arrays.asList(o.getClass().getFields()), flags, null, true);
    }

    /** see {@link #setFieldsFromFlags(Object o, ConfigBag)} */
    public static Map<?, ?> setFieldsFromFlags(Map<?, ?> flags, Object o) {
        return setFieldsFromFlagsInternal(o, getAllFields(o.getClass()), flags, null, true);
    }
	
    /** sets all fields (including private and static, local and inherited) annotated {@link SetFromFlag} on the given object, 
     * from the given flags map, returning just those flag-value pairs passed in which do not correspond to SetFromFlags fields 
     * annotated ConfigKey and HasConfigKey fields are _configured_ (and we assume the object in that case is {@link Configurable});
     * keys should be ConfigKey, HasConfigKey, or String;
     * default values are also applied unless that is specified false on one of the variants of this method which takes such an argument
     */
    public static void setFieldsFromFlags(Object o, ConfigBag configBag) {
        setFieldsFromFlagsInternal(o, getAllFields(o.getClass()), configBag.getAllConfig(), configBag, true);
    }

    /** as {@link #setFieldsFromFlags(Object o, ConfigBag)} */
    public static void setFieldsFromFlags(Object o, ConfigBag configBag, boolean setDefaultVals) {
        setFieldsFromFlagsInternal(o, getAllFields(o.getClass()), configBag.getAllConfig(), configBag, setDefaultVals);
    }

    /** as {@link #setFieldsFromFlags(Object o, ConfigBag)}, but specifying a subset of flags to use */
    public static void setFieldsFromFlagsWithBag(Object o, Map<?,?> flags, ConfigBag configBag, boolean setDefaultVals) {
        setFieldsFromFlagsInternal(o, getAllFields(o.getClass()), flags, configBag, setDefaultVals);
    }

    /**
     * Sets the field with the given flag (if it exists) to the given value.
     * Will attempt to coerce the value to the required type.
     * Will respect "nullable" on the SetFromFlag annotation.
     * 
     * @throws IllegalArgumentException If fieldVal is null and the SetFromFlag annotation set nullable=false
     */
    public static boolean setFieldFromFlag(Object o, String flagName, Object fieldVal) {
        return setFieldFromFlagInternal(checkNotNull(flagName, "flagName"), fieldVal, o, getAllFields(o.getClass()));
    }
    
    /** get all fields (including private and static) on the given object and all supertypes, 
     * that are annotated with SetFromFlags. 
     */
    public static Map<String, ?> getFieldsWithFlags(Object o) {
        return getFieldsWithFlagsInternal(o, getAllFields(o.getClass()));
    }
	
    /**
     * Finds the {@link Field} on the given object annotated with the given name flag.
     */
    public static Field findFieldForFlag(String flagName, Object o) {
    	return findFieldForFlagInternal(flagName, o, getAllFields(o.getClass()));
    }

    /** get all fields (including private and static) and their values on the given object and all supertypes, 
     * where the field is annotated with SetFromFlags. 
     */
    public static Map<String, Object> getFieldsWithFlagsExcludingModifiers(Object o, int excludingModifiers) {
        List<Field> filteredFields = Lists.newArrayList();
        for (Field contender : getAllFields(o.getClass())) {
        	if ((contender.getModifiers() & excludingModifiers) == 0) {
        		filteredFields.add(contender);
        	}
        }
		return getFieldsWithFlagsInternal(o, filteredFields);
    }
    
    /** get all fields with the given modifiers, and their values on the given object and all supertypes, 
     * where the field is annotated with SetFromFlags. 
     */
    public static Map<String, Object> getFieldsWithFlagsWithModifiers(Object o, int requiredModifiers) {
        List<Field> filteredFields = Lists.newArrayList();
        for (Field contender : getAllFields(o.getClass())) {
            if ((contender.getModifiers() & requiredModifiers) == requiredModifiers) {
                filteredFields.add(contender);
            }
        }
        return getFieldsWithFlagsInternal(o, filteredFields);
    }
    
    /** sets _all_ accessible _{@link ConfigKey}_ and {@link HasConfigKey} fields on the given object, 
     * using the indicated flags/config-bag 
     * @deprecated since 0.7.0 use {@link #setAllConfigKeys(Map, Configurable, boolean)} */
    public static Map<String, ?> setAllConfigKeys(Map<String, ?> flagsOrConfig, Configurable instance) {
        return setAllConfigKeys(flagsOrConfig, instance, false);
    }
    /** sets _all_ accessible _{@link ConfigKey}_ and {@link HasConfigKey} fields on the given object, 
     * using the indicated flags/config-bag */
    public static Map<String, ?> setAllConfigKeys(Map<String, ?> flagsOrConfig, Configurable instance, boolean includeFlags) {
        ConfigBag bag = new ConfigBag().putAll(flagsOrConfig);
        setAllConfigKeys(instance, bag, includeFlags);
        return bag.getUnusedConfigMutable();
    }
    
    /** sets _all_ accessible _{@link ConfigKey}_ and {@link HasConfigKey} fields on the given object, 
     * using the indicated flags/config-bag 
    * @deprecated since 0.7.0 use {@link #setAllConfigKeys(Configurable, ConfigBag, boolean)} */
    public static void setAllConfigKeys(Configurable o, ConfigBag bag) {
        setAllConfigKeys(o, bag, false);
    }
    /** sets _all_ accessible _{@link ConfigKey}_ and {@link HasConfigKey} fields on the given object, 
     * using the indicated flags/config-bag */
    public static void setAllConfigKeys(Configurable o, ConfigBag bag, boolean includeFlags) {
        for (Field f: getAllFields(o.getClass())) {
            ConfigKey<?> key = getFieldAsConfigKey(o, f);
            if (key!=null) {
                FlagConfigKeyAndValueRecord record = getFlagConfigKeyRecord(f, key, bag);
                if (record.isValuePresent())
                    setField(o, f, record.getValueOrNullPreferringConfigKey(), null);
            }
        }
    }
    
    public static class FlagConfigKeyAndValueRecord {
        private String flagName = null;
        private ConfigKey<?> configKey = null;
        private Maybe<Object> flagValue = Maybe.absent();
        private Maybe<Object> configKeyValue = Maybe.absent();
        
        public String getFlagName() {
            return flagName;
        }
        public ConfigKey<?> getConfigKey() {
            return configKey;
        }
        public Maybe<Object> getFlagMaybeValue() {
            return flagValue;
        }
        public Maybe<Object> getConfigKeyMaybeValue() {
            return configKeyValue;
        }
        public Object getValueOrNullPreferringConfigKey() {
            return getConfigKeyMaybeValue().or(getFlagMaybeValue()).orNull();
        }
        public Object getValueOrNullPreferringFlag() {
            return getFlagMaybeValue().or(getConfigKeyMaybeValue()).orNull();
        }
        /** true if value is present for either flag or config key */
        public boolean isValuePresent() {
            return flagValue.isPresent() || configKeyValue.isPresent();
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).omitNullValues()
                .add("flag", flagName)
                .add("configKey", configKey)
                .add("flagValue", flagValue.orNull())
                .add("configKeyValue", configKeyValue.orNull())
                .toString();
        }
    }
    
    /** gets all the flags/keys in the given config bag which are applicable to the given type's config keys and flags */
    public static <T> List<FlagConfigKeyAndValueRecord> findAllFlagsAndConfigKeys(T optionalInstance, Class<? extends T> type, ConfigBag input) {
        List<FlagConfigKeyAndValueRecord> output = new ArrayList<FlagUtils.FlagConfigKeyAndValueRecord>();
        for (Field f: getAllFields(type)) {
            ConfigKey<?> key = getFieldAsConfigKey(optionalInstance, f);
            FlagConfigKeyAndValueRecord record = getFlagConfigKeyRecord(f, key, input);
            if (record.isValuePresent())
                output.add(record);
        }
        return output;
    }

    /** returns the flag/config-key record for the given input */
    private static FlagConfigKeyAndValueRecord getFlagConfigKeyRecord(Field f, ConfigKey<?> key, ConfigBag input) {
        FlagConfigKeyAndValueRecord result = new FlagConfigKeyAndValueRecord(); 
        result.configKey = key;
        if (key!=null && input.containsKey(key))
            result.configKeyValue = Maybe.<Object>of(input.getStringKey(key.getName()));
        SetFromFlag flag = f.getAnnotation(SetFromFlag.class);
        if (flag!=null) {
            result.flagName = flag.value();
            if (input.containsKey(flag.value()))
                result.flagValue = Maybe.of(input.getStringKey(flag.value()));
        }
        return result;
    }

	/** returns all fields on the given class, superclasses, and interfaces thereof, in that order of preference,
	 * (excluding fields on Object) */
    public static List<Field> getAllFields(Class<?> base, Closure<Boolean> filter) {
        return getAllFields(base, GroovyJavaMethods.<Field>predicateFromClosure(filter));
    }
    public static List<Field> getAllFields(Class<?> base) {
        return getAllFields(base, Predicates.<Field>alwaysTrue());
    }
	public static List<Field> getAllFields(Class<?> base, Predicate<Field> filter) {
		return getLocalFields(getAllAssignableTypes(base), filter);
	}
	/** returns all fields explicitly declared on the given classes */
    public static List<Field> getLocalFields(List<Class<?>> classes) {
        return getLocalFields(classes, Predicates.<Field>alwaysTrue());
    }
    public static List<Field> getLocalFields(List<Class<?>> classes, Closure<Boolean> filter) {
        return getLocalFields(classes, GroovyJavaMethods.<Field>predicateFromClosure(filter));
    }
	public static List<Field> getLocalFields(List<Class<?>> classes, Predicate<Field> filter) {
		List<Field> fields = Lists.newArrayList();
		for (Class<?> c : classes) {
		    for (Field f : c.getDeclaredFields()) {
		        if (filter.apply(f)) fields.add(f);
	        }
	    }
		return fields;
	}
	
	/** returns base, superclasses, then interfaces */
    public static List<Class<?>> getAllAssignableTypes(Class<?> base) {
        return getAllAssignableTypes(base, new Predicate<Class<?>>() {
            @Override public boolean apply(Class<?> it) {
                return (it != Object.class) && (it != GroovyObject.class);
            }
        });
    }
    public static List<Class<?>> getAllAssignableTypes(Class<?> base, Closure<Boolean> filter) {
        return getAllAssignableTypes(base, GroovyJavaMethods.<Class<?>>predicateFromClosure(filter));
    }
	public static List<Class<?>> getAllAssignableTypes(Class<?> base, Predicate<Class<?>> filter) {
		List<Class<?>> classes = Lists.newArrayList();
		for (Class<?> c = base; c != null; c = c.getSuperclass()) {
		    if (filter.apply(c)) classes.add(c);
	    }
		for (int i=0; i<classes.size(); i++) {
			for (Class<?> interf : classes.get(i).getInterfaces()) {
			    if (filter.apply(interf) && !(classes.contains(interf))) classes.add(interf);
		    }
		}
		return classes;
	}
	
    private static Map<String, Object> getFieldsWithFlagsInternal(Object o, Collection<Field> fields) {
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null) {
                String flagName = elvis(cf.value(), f.getName());
                if (truth(flagName)) {
                	result.put(flagName, getField(o, f));
                } else {
                	log.warn("Ignoring field {} of object {} as no flag name available", f, o);
                }
            }
        }
        return result;
    }

    private static Field findFieldForFlagInternal(String flagName, Object o, Collection<Field> fields) {
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null) {
            	String contenderName = elvis(cf.value(), f.getName());
                if (flagName.equals(contenderName)) {
                	return f;
                }
            }
        }
        throw new NoSuchElementException("Field with flag "+flagName+" not found on "+o+" of type "+(o != null ? o.getClass() : null));
    }

    private static boolean setFieldFromFlagInternal(String flagName, Object fieldVal, Object o, Collection<Field> fields) {
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null && flagName.equals(elvis(cf.value(), f.getName()))) {
                setField(o, f, fieldVal, cf);
                return true;
            }
        }
        return false;
    }

    private static Map<String, ?> setFieldsFromFlagsInternal(Object o, Collection<Field> fields, Map<?,?> flagsOrConfig, ConfigBag bag, boolean setDefaultVals) {
        if (bag==null) bag = new ConfigBag().putAll(flagsOrConfig);
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf!=null) setFieldFromConfig(o, f, bag, cf, setDefaultVals);
        }
        return bag.getUnusedConfigMutable();
    }

    private static void setFieldFromConfig(Object o, Field f, ConfigBag bag, SetFromFlag optionalAnnotation, boolean setDefaultVals) {
        String flagName = optionalAnnotation==null ? null : (String)elvis(optionalAnnotation.value(), f.getName());
        // prefer flag name, if present
        if (truth(flagName) && bag.containsKey(flagName)) {
            setField(o, f, bag.getStringKey(flagName), optionalAnnotation);
            return;
        }
        // first check whether it is a key
        ConfigKey<?> key = getFieldAsConfigKey(o, f);
        if (key!=null && bag.containsKey(key)) {
            Object uncoercedValue = bag.getStringKey(key.getName());
            setField(o, f, uncoercedValue, optionalAnnotation);
            return;
        }
        if (setDefaultVals && optionalAnnotation!=null && truth(optionalAnnotation.defaultVal())) {
            Object oldValue;
            try {
                f.setAccessible(true);
                oldValue = f.get(o);
                if (oldValue==null || oldValue.equals(getDefaultValueForType(f.getType()))) {
                    setField(o, f, optionalAnnotation.defaultVal(), optionalAnnotation);
                }
            } catch (Exception e) {
                Exceptions.propagate(e);
            }
            return;
        }
    }

    /** returns the given field as a config key, if it is an accessible config key, otherwise null */
    private static ConfigKey<?> getFieldAsConfigKey(Object optionalInstance, Field f) {
        if (optionalInstance==null) {
            if ((f.getModifiers() & Modifier.STATIC)==0)
                // non-static field on null instance, can't be set
                return null;
        }
        if (ConfigKey.class.isAssignableFrom(f.getType())) {
            return (ConfigKey<?>) getField(optionalInstance, f);
        } else if (HasConfigKey.class.isAssignableFrom(f.getType())) {
            return ((HasConfigKey<?>)getField(optionalInstance, f)).getConfigKey();
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void setConfig(Object objectOfField, ConfigKey<?> key, Object value, SetFromFlag optionalAnnotation) {
        if (objectOfField instanceof Configurable) {
            ((Configurable)objectOfField).setConfig((ConfigKey)key, value);
            return;
        } else {
            if (optionalAnnotation==null) {
                log.warn("Cannot set key "+key.getName()+" on "+objectOfField+": containing class is not Configurable");
            } else if (!key.getName().equals(optionalAnnotation.value())) {
                log.warn("Cannot set key "+key.getName()+" on "+objectOfField+" from flag "+optionalAnnotation.value()+": containing class is not Configurable");
            } else {
                // if key and flag are the same, then it will probably happen automatically
                if (log.isDebugEnabled())
                    log.debug("Cannot set key "+key.getName()+" on "+objectOfField+" from flag "+optionalAnnotation.value()+": containing class is not Configurable");
            }
            return;
        }
    }
    
    /** sets the field to the value, after checking whether the given value can be set 
     * respecting the constraints of the annotation 
     */
    public static void setField(Object objectOfField, Field f, Object value, SetFromFlag optionalAnnotation) {
        try {
            ConfigKey<?> key = getFieldAsConfigKey(objectOfField, f);
            if (key!=null) {
                setConfig(objectOfField, key, value, optionalAnnotation);
                return;
            }
            
            if (!f.isAccessible()) f.setAccessible(true);
            if (optionalAnnotation!=null && optionalAnnotation.immutable()) {
                Object oldValue = f.get(objectOfField);
                if (!Objects.equal(oldValue, getDefaultValueForType(f.getType())) && oldValue != value) {
                    throw new IllegalStateException("Forbidden modification to immutable field "+
                        f+" in "+objectOfField+": attempting to change to "+value+" when was already "+oldValue);
                }
            }
            if (optionalAnnotation!=null && !optionalAnnotation.nullable() && value==null) {
                throw new IllegalArgumentException("Forbidden null assignment to non-nullable field "+
                        f+" in "+objectOfField);
            }
            if (optionalAnnotation!=null && (f.getModifiers() & Modifier.STATIC)==Modifier.STATIC)
                log.warn("Setting static field "+f+" in "+objectOfField+" from flag "+optionalAnnotation.value()+": discouraged");

            Object newValue;
            try {
                newValue = TypeCoercions.coerce(value, f.getType());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot set "+f+" in "+objectOfField+" from type "+value.getClass()+" ("+value+"): "+e, e);
            }
            f.set(objectOfField, newValue);
            if (log.isTraceEnabled()) log.trace("FlagUtils for "+objectOfField+", setting field="+f.getName()+"; val="+value+"; newVal="+newValue+"; key="+key);

        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

    /** gets the value of the field. 
     */
    public static Object getField(Object objectOfField, Field f) {
        try {
            if (!f.isAccessible()) f.setAccessible(true);
            return f.get(objectOfField);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
    
    /** returns the default/inital value that is assigned to fields of the givien type;
     * if the type is not primitive this value is null;
     * for primitive types it is obvious but not AFAIK programmatically visible
     * (e.g. 0 for int, false for boolean)  
     */
    public static Object getDefaultValueForType(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t==Integer.TYPE) return (int)0;
        if (t==Long.TYPE) return (long)0;
        if (t==Double.TYPE) return (double)0;
        if (t==Float.TYPE) return (float)0;
        if (t==Byte.TYPE) return (byte)0;
        if (t==Short.TYPE) return (short)0;
        if (t==Character.TYPE) return (char)0;
        if (t==Boolean.TYPE) return false;
        //should never happen
        throw new IllegalStateException("Class "+t+" is an unknown primitive.");
    }

    /** returns a map of all fields which are annotated 'SetFromFlag', along with the annotation */
    public static Map<Field,SetFromFlag> getAnnotatedFields(Class<?> type) {
        Map<Field, SetFromFlag> result = Maps.newLinkedHashMap();
        for (Field f: getAllFields(type)) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (truth(cf)) result.put(f, cf);
        }
        return result;
    }

    /** returns a map of all {@link ConfigKey} fields which are annotated 'SetFromFlag', along with the annotation */
    public static Map<ConfigKey<?>,SetFromFlag> getAnnotatedConfigKeys(Class<?> type) {
        Map<ConfigKey<?>, SetFromFlag> result = Maps.newLinkedHashMap();
        List<Field> fields = getAllFields(type, new Predicate<Field>() {
            @Override public boolean apply(Field f) {
                return (f != null) && ConfigKey.class.isAssignableFrom(f.getType()) && ((f.getModifiers() & Modifier.STATIC)!=0);
            }});
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null) {
                ConfigKey<?> key = getFieldAsConfigKey(null, f);
                if (key != null) {
                    result.put(key, cf);
                }
            }
        }
        return result;
    }

	/** returns a map of all fields which are annotated 'SetFromFlag' with their current values;
	 * useful if you want to clone settings from one object
	 */
	public static Map<String,Object> getFieldsWithValues(Object o) {
	    try {
            Map<String, Object> result = Maps.newLinkedHashMap();
            for (Map.Entry<Field, SetFromFlag> entry : getAnnotatedFields(o.getClass()).entrySet()) {
                Field f = entry.getKey();
                SetFromFlag cf = entry.getValue();
                String flagName = elvis(cf.value(), f.getName());
                if (truth(flagName)) {
                    if (!f.isAccessible()) f.setAccessible(true);
                    result.put(flagName, f.get(o));
                }
    		}
    		return result;
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
	}
        
    /**
     * @throws an IllegalStateException if there are fields required (nullable=false) which are unset 
     * @throws wrapped IllegalAccessException
     */
    public static void checkRequiredFields(Object o) {
        try {
            Set<String> unsetFields = Sets.newLinkedHashSet();
            for (Map.Entry<Field, SetFromFlag> entry : getAnnotatedFields(o.getClass()).entrySet()) {
                Field f = entry.getKey();
                SetFromFlag cf = entry.getValue();
                if (!cf.nullable()) {
                    String flagName = elvis(cf.value(), f.getName());
                    if (!f.isAccessible()) f.setAccessible(true);
                    Object v = f.get(o);
                    if (v==null) unsetFields.add(flagName);
                }
            }
            if (truth(unsetFields)) {
                throw new IllegalStateException("Missing required "+(unsetFields.size()>1 ? "fields" : "field")+": "+unsetFields);
            }
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

//    /** sets all fields in target annotated with @SetFromFlag using the configuration in the given config bag */
//    public static void setFieldsFromConfigFlags(Object target, ConfigBag configBag) {
//        setFieldsFromConfigFlags(target, configBag.getAllConfig(), configBag);
//    }
//
//    
//    /** sets all fields in target annotated with @SetFromFlag using the configuration in the given configToUse,
//     * marking used in the given configBag */
//    public static void setFieldsFromConfigFlags(Object target, Map<?,?> configToUse, ConfigBag configBag) {
//        for (Map.Entry<?,?> entry: configToUse.entrySet()) {
//            setFieldFromConfigFlag(target, entry.getKey(), entry.getValue(), configBag);
//        }
//    }
//
//    public static void setFieldFromConfigFlag(Object target, Object key, Object value, ConfigBag optionalConfigBag) {
//        String name = null;
//        if (key instanceof String) name = (String)key;
//        else if (key instanceof ConfigKey<?>) name = ((ConfigKey<?>)key).getName();
//        else if (key instanceof HasConfigKey<?>) name = ((HasConfigKey<?>)key).getConfigKey().getName();
//        else {
//            if (key!=null) {
//                log.warn("Invalid config type "+key.getClass().getCanonicalName()+" ("+key+") when configuring "+target+"; ignoring");
//            }
//            return;
//        }
//        if (setFieldFromFlag(name, value, target)) {
//            if (optionalConfigBag!=null)
//                optionalConfigBag.markUsed(name);
//        }
//    }
    
}
