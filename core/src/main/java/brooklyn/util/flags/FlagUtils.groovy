package brooklyn.util.flags;

import java.lang.reflect.Field


/** class to help transfer values passed as named arguments to other well-known variables/fields/objects;
 * see the test case for example usage */
public class FlagUtils {

    private FlagUtils() {}
    
    /** sets all public fields (local and inherited) on the given object from the given flags map, returning unknown elements */
    public static Map setPublicFieldsFromFlags(Map flags, Object o) {
        setFieldsFromFlags(flags, o, o.getClass().getFields() as Set)
    }
    /** sets all fields (including private and static) on the given object and all supertypes, 
     * from the given flags map, returning just those flags which are not applicable */
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
        Map remaining=[:]+flags
        for (Field f: fields) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf) {
                String flagName = cf.value() ?: f.getName();
                if (flagName && remaining.containsKey(flagName)) {
                    if (!f.isAccessible()) f.setAccessible(true)
                    f.set(o, remaining.remove(flagName))
                }
            }
        }
        return remaining
    }

	/** returns a map of all fields which are annotated 'SetFromFlag' with their current values;
	 * useful if you want to clone settings from one object
	 */
	public static Map getFieldsWithValues(Object o) {
		Map result=[:]
		for (Field f: getAllFields(o.getClass())) {
			SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
			if (cf) {
				String flagName = cf.value() ?: f.getName();
				if (flagName) {
					if (!f.isAccessible()) f.setAccessible(true)
					result.put(flagName, f.get(o))
				}
			}
		}
		return result
	}
}
