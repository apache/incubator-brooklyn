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
    /** sets all locally declared fields (including private) and all public inherited fields on the given object from the given flags map, returning unknown elements */
    public static Map setFieldsFromFlags(Map flags, Object o) {
        setFieldsFromFlags(flags, o, (o.getClass().getDeclaredFields() as Set) + (o.getClass().getFields() as Set))
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
		def fields = o.getClass().getFields()
		for (Field f: fields) {
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
