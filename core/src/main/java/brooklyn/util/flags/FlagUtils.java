package brooklyn.util.flags;

import static com.google.common.base.Preconditions.checkNotNull;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;

import java.lang.reflect.Field;
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
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.util.GroovyJavaMethods;

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
    
    /** 
     * sets all public fields (local and inherited) on the given object from the given flags map, returning unknown elements
     */
    public static Map<String, ? extends Object> setPublicFieldsFromFlags(Map<String, ? extends Object> flags, Object o) {
        return setFieldsFromFlagsInternal(flags, o, Arrays.asList(o.getClass().getFields()));
    }
    
    /** sets all fields (including private and static) on the given object and all supertypes, 
     * from the given flags map, returning just those flag-value pairs passed in which do not correspond to SetFromFlags fields 
     */
    public static Map<String, ? extends Object> setFieldsFromFlags(Map<String, ? extends Object> flags, Object o) {
        return setFieldsFromFlagsInternal(flags, o, getAllFields(o.getClass()));
    }
	
    /**
     * Sets the field with the given flag (if it exists) to the given value.
     * Will attempt to coerce the value to the required type.
     * Will respect "nullable" on the SetFromFlag annotation.
     * 
     * @throws IllegalArgumentException If fieldVal is null and the SetFromFlag annotation set nullable=false
     */
    public static void setFieldFromFlag(String flagName, Object fieldVal, Object o) {
        setFieldFromFlagInternal(checkNotNull(flagName, "flagName"), fieldVal, o, getAllFields(o.getClass()));
    }
    
    /** get all fields (including private and static) on the given object and all supertypes, 
     * that are annotated with SetFromFlags. 
     */
    public static Map<String, ? extends Object> getFieldsWithFlags(Object o) {
        return getFieldsWithFlagsInternal(o, getAllFields(o.getClass()));
    }
	
    /**
     * Finds the {@link Field} on the given object annotated with the given name flag.
     */
    public static Field findFieldForFlag(String flagName, Object o) {
    	return findFieldForFlagInternal(flagName, o, getAllFields(o.getClass()));
    }

    /** get all fields (including private and static) on the given object and all supertypes, 
     * that are annotated with SetFromFlags. 
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
    
    // TODO Don't want to use class AbstractEntity here...
    public static Map<String, ? extends Object> setConfigKeysFromFlags(Map<String, ? extends Object> flags, AbstractEntity entity) {
        return setConfigKeysFromFlagsInternal(flags, entity, getAllFields(entity.getClass()));
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

    private static void setFieldFromFlagInternal(String flagName, Object fieldVal, Object o, Collection<Field> fields) {
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null && flagName.equals(elvis(cf.value(), f.getName()))) {
                setField(o, f, fieldVal, cf);
                break;
            }
        }
    }

    private static Map<String, ? extends Object> setFieldsFromFlagsInternal(Map<String,? extends Object> flags, Object o, Collection<Field> fields) {
        Map<String, Object> remaining = Maps.newLinkedHashMap();
		if (truth(flags)) remaining.putAll(flags);
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null) {
                String flagName = elvis(cf.value(), f.getName());
                if (truth(flagName) && remaining.containsKey(flagName)) {
                    setField(o, f, remaining.remove(flagName), cf);
                } else if (truth(cf.defaultVal())) {
                    setField(o, f, cf.defaultVal(), cf);
                }
            }
        }
        return remaining;
    }

    private static Map<String, ? extends Object> setConfigKeysFromFlagsInternal(Map<String,? extends Object> flags, AbstractEntity entity, Collection<Field> fields) {
        Map<String, Object> remaining = Maps.newLinkedHashMap();
        if (truth(flags)) remaining.putAll(flags);
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf != null) {
                ConfigKey key = null;
                if (ConfigKey.class.isAssignableFrom(f.getType())) {
                    key = (ConfigKey) getField(entity, f);
                } else if (HasConfigKey.class.isAssignableFrom(f.getType())) {
                    key = ((HasConfigKey)getField(entity, f)).getConfigKey();
                } else {
                    
                }
                if (key != null) {
                    String flagName = elvis(cf.value(), key.getName());
                    if (truth(flagName) && remaining.containsKey(flagName)) {
                        Object v = remaining.remove(flagName);
                        entity.setConfig(key, v);
                    }
                }
            }
        }
        return remaining;
    }

    /** sets the field to the value, after checking whether the given value can be set 
     * respecting the constraints of the annotation 
     */
    public static void setField(Object objectOfField, Field f, Object value, SetFromFlag annotation) {
        try {
            if (!f.isAccessible()) f.setAccessible(true);
            if (annotation.immutable()) {
                Object oldValue = f.get(objectOfField);
                if (!Objects.equal(oldValue, getDefaultValueForType(f.getType())) && oldValue != value) {
                    throw new IllegalStateException("Forbidden modification to immutable field "+
                        f+" in "+objectOfField+": attempting to change to "+value+" when was already "+oldValue);
                }
            }
            if (!annotation.nullable() && value==null) {
                throw new IllegalArgumentException("Forbidden null assignment to non-nullable field "+
                        f+" in "+objectOfField);
            }
            Object newValue;
            try {
                // TODO Should this code be pushed into TypeCoercions.coerce itself?
                Class<?> fieldType = f.getType();
                newValue = TypeCoercions.coerce(value, fieldType);
                
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot set "+f+" in "+objectOfField+" from type "+value.getClass()+" ("+value+"): "+e, e);
            }
            f.set(objectOfField, newValue);
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
}
