package brooklyn.util.flags;

import java.lang.reflect.Constructor
import java.lang.reflect.Field

import org.slf4j.Logger
import org.slf4j.LoggerFactory


/** class to help transfer values passed as named arguments to other well-known variables/fields/objects;
 * see the test case for example usage */
public class FlagUtils {

    public static final Logger log = LoggerFactory.getLogger(FlagUtils.class);
    
    private FlagUtils() {}
    
    /** sets all public fields (local and inherited) on the given object from the given flags map, returning unknown elements */
    public static Map setPublicFieldsFromFlags(Map flags, Object o) {
        setFieldsFromFlags(flags, o, o.getClass().getFields() as Set)
    }
    /** sets all fields (including private and static) on the given object and all supertypes, 
     * from the given flags map, returning just those flag-value pairs passed in which do not correspond to SetFromFlags fields */
    public static Map setFieldsFromFlags(Map flags, Object o) {
        setFieldsFromFlags(flags, o, getAllFields(o.getClass()))
    }
	
	/** returns all fields on the given class, superclasses, and interfaces thereof, in that order of preference,
	 * (excluding fields on Object) */
	public static List getAllFields(Class base, Closure filter={true}) {
		getLocalFields(getAllAssignableTypes(base), filter);
	}
	/** returns all fields explicitly declared on the given classes */
	public static List getLocalFields(List classes, Closure filter={true}) {
		List fields = []
		classes.each { Class c -> c.getDeclaredFields().each { Field f -> if (filter.call(f)) fields << f }}
		fields
	}
	/** returns base, superclasses, then interfaces */
	public static List getAllAssignableTypes(Class base, Closure filter={ (it!=Object) && (it!=GroovyObject) }) {
		List classes = []
		for (Class c = base; c!=null; c=c.getSuperclass()) { if (filter.call(c)) classes << c }
		for (int i=0; i<classes.size(); i++) {
			classes.get(i).getInterfaces().each { c -> if (filter.call(c) && !(classes.contains(c))) classes << c } 
		}
		classes
	}
	
    private static Map setFieldsFromFlags(Map flags, Object o, Collection<Field> fields) {
        Map remaining=[:]
		if (flags) remaining += flags
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf) {
                String flagName = cf.value() ?: f.getName();
                if (flagName && remaining.containsKey(flagName)) {
                    setField(o, f, remaining.remove(flagName), cf);
                }
            }
        }
        return remaining
    }

    /** sets the field to the value, after checking whether the given value can be set 
     * respecting the constraints of the annotation */
    public static Object setField(Object objectOfField, Field f, Object value, SetFromFlag annotation) {
        if (!f.isAccessible()) f.setAccessible(true)
        if (annotation.immutable()) {
            def oldValue = f.get(objectOfField)
            if (oldValue!=getDefaultValueForType(f.getType()) && oldValue!=value) {
                throw new IllegalStateException("Forbidden modification to immutable field "+
                    "$f in $objectOfField: attempting to change to $value when was already $oldValue");
            }
        }
        if (!annotation.nullable() && value==null) {
            throw new IllegalArgumentException("Forbidden null assignment to non-nullable field "+
                    "$f in $objectOfField");
        }
        Object newValue = value;
        Class targetType = f.getType();
        if (value==null || targetType.isAssignableFrom(value.getClass()) || targetType.isPrimitive() || value.getClass().isPrimitive()) {
            //nothing needs doing
            //(the primitive check is not 100% but workaround to avoid the complex boxing+conversion logic java has)
        } else {
            //coercion required
            if (targetType.isInterface()) {
                targetType = getDefaultConcreteTypeForInterface(targetType);
                if (targetType==null) {
                    throw new IllegalArgumentException("Cannot set "+f+" ("+f.getType()+") from "+value+" ("+value.getClass()+"); no default type available for interface");
                }                
            }
            Constructor targetC = getMatchingConstructor(targetType, value);
            if (targetC!=null) {
                if (Collection.class.isAssignableFrom(f.getType()) && value in Collection) {}
                else if (Map.class.isAssignableFrom(f.getType()) && value in Map) {}
                else {
                    throw new IllegalArgumentException("Cannot set "+f+" ("+targetType+") from "+value+" ("+value.getClass()+"); explicit constructor conversion required");
                }
                newValue = targetC.newInstance( value );
            } else {
                throw new IllegalArgumentException("Cannot set "+f+" ("+targetType+") from "+value+" ("+value.getClass()+"); no conversion known");
            }
        }
        f.set(objectOfField, newValue)
    }
    
    /** returns the default/inital value that is assigned to fields of the givien type;
     * if the type is not primitive this value is null;
     * for primitive types it is obvious but not AFAIK programmatically visible
     * (e.g. 0 for int, false for boolean)  
     */
    public static Object getDefaultValueForType(Class t) {
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
        throw new IllegalStateException("Class $t is an unknown primitive.");
    }
    
    /** for automatic conversion */
    public static Object getDefaultConcreteTypeForInterface(Class t) {
        if (t==Set) return LinkedHashSet;
        if (t==List) return ArrayList;
        if (t==Map) return LinkedHashMap;
        return null;
    }

    /** for automatic conversion */
    public static Object getMatchingConstructor(Class target, Object ...arguments) {
        Constructor[] cc = target.getConstructors();
        for (Constructor c: cc) {
            if (c.getParameterTypes().length != arguments.length)
                continue;
            boolean matches = true;
            Class[] tt = c.getParameterTypes();
            for (int i=0; i<tt.length; i++) {
                if (arguments[i]!=null && !tt[i].isInstance(arguments[i])) {
                    matches=false;
                    break;
                }
            }
            if (matches) 
                return c;
        }
        return null;
    }

    /** returns a map of all fields which are annotated 'SetFromFlag', along with the annotation */
    public static Map<Field,SetFromFlag> getAnnotatedFields(Class type) {
        Map result=[:]
        for (Field f: getAllFields(type)) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf) result.put(f, cf);
        }
        return result
    }

	/** returns a map of all fields which are annotated 'SetFromFlag' with their current values;
	 * useful if you want to clone settings from one object
	 */
	public static Map<String,Object> getFieldsWithValues(Object o) {
        Map result=[:]
        getAnnotatedFields(o.getClass()).each { Field f, SetFromFlag cf ->
            String flagName = cf.value() ?: f.getName();
            if (flagName) {
                if (!f.isAccessible()) f.setAccessible(true)
                result.put(flagName, f.get(o))
            }
		}
		return result
	}
        
    /** throws an IllegalStateException if there are fields required (nullable=false) which are unset */
    public static void checkRequiredFields(Object o) {
        Set result=[]
        getAnnotatedFields(o.getClass()).each { Field f, SetFromFlag cf ->
            if (!cf.nullable()) {
                String flagName = cf.value() ?: f.getName();
                if (!f.isAccessible()) f.setAccessible(true)
                Object v = f.get(o)
                if (v==null) result += flagName
            }
        }
        if (result) {
            throw new IllegalStateException("Missing required "+(result.size()>1 ? "fields" : "field")+": "+result);
        }
    }

}
