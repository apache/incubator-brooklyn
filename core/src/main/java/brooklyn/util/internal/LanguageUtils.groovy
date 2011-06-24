package brooklyn.util.internal

import java.lang.reflect.Modifier
import java.util.Collection
import java.util.Map

import org.codehaus.groovy.util.HashCodeHelper;

import com.thoughtworks.xstream.XStream

/**
 * @author alex
 */
public class LanguageUtils {
	static <T> T getRequiredField(String name, Map<?,?> m) {
		if (!m.containsKey(name))
			throw new IllegalArgumentException("a parameter '"+name+"' was required in the argument to this function")
		m.get name
	}
	
	static <T> T getOptionalField(String name, Map<?,?> m, T defaultValue=null) {
		m.get(name) ?: defaultValue
	}
	
	static <T> T getPropertySafe(Object target, String name, T defaultValue=null) {
		target.hasProperty(name)?.getProperty(target) ?: defaultValue
	}
	
	//TODO find with annotation
	
	public static byte[] serialize(Object orig) {
		if (orig == null) return null;
		
		// Write the object out to a byte array
		ByteArrayOutputStream fbos = []
		ObjectOutputStream out = new ObjectOutputStream(fbos);
		out.writeObject(orig);
		out.flush();
		out.close();
		return fbos.toByteArray();
	}
	
	public static <T> T deserialize(byte[] bytes, ClassLoader classLoader) {
		if (bytes == null) return null;
		
		ObjectInputStream ins =
				//new ObjectInputStreamWithLoader(new FastByteArrayInputStream(bytes, bytes.length), classLoader);
				new ObjectInputStream(new ByteArrayInputStream(bytes));
		(T) ins.readObject();
	}
	
	static <T> T clone(T src) {
        XStream xstream = new XStream();
        xstream.setClassLoader(src.getClass().getClassLoader())
//      use (xstream) { fromXML(toXML(src)) }
        xstream.fromXML(xstream.toXML(src))
		
//		// TODO investigate XStream.  or json.
//		deserialize(serialize(src), src.getClass().getClassLoader());
	}
	
	static String newUid() { Integer.toHexString((Integer)new Random().nextInt()) }

	public static Map setFieldsFromMap(Object target, Map fieldValues) {
		Map unused = [:]
		fieldValues.each {
//			println "looking for "+it.key+" in "+target+": "+target.metaClass.hasProperty(it.key) 
			target.hasProperty(it.key) ? target.(it.key) = it.value : unused << it 
		}
		unused
	}

	/**
	 * Visits all fields of a given object, recursively.
	 * 
	 * For collections, arrays, and maps it visits the items within, passing null for keys where it isn't a map.
	 */
	private static void visitFields(Object o, FieldVisitor fv, Collection<Object> objectsToSkip=([] as Set)) {
		if (o==null || objectsToSkip.contains(o)) return;
		objectsToSkip << o
		if (o in String) return;
		if (o in Map) {
			o.each { fv.visit(o, it.key.toString(), it.value); visitFields(it.value, fv, objectsToSkip) }
		} else if ((o in Collection) || (o.getClass().isArray())) { 
			o.each { fv.visit(o, null, it); visitFields(it, fv, objectsToSkip) }
		} else {
			o.getClass().getDeclaredFields().each { if ((it.getModifiers() & Modifier.STATIC) || it.isSynthetic()) return;  //skip static
				it.setAccessible true; def v = it.get(o); 
				//println "field $it value $v";
				fv.visit(o, it.name, v); visitFields(v, fv, objectsToSkip) 
		} }
	}
	public interface FieldVisitor {
		/** invoked by visitFields; fieldName will be null for collections */
		public void visit(Object parent, String fieldName, Object value)
	}
	
	public static <T> T throwRuntime(Throwable t) {
		if (t instanceof RuntimeException) throw (RuntimeException)t;
		if (t instanceof Error) throw (Error)t;
		throw new RuntimeException(t);
	}

	public static Throwable getRoot(Throwable t) {
		Throwable cause = t.getCause()
		if (!cause) return t
		if (cause==t) return t
		return getRoot(cause)
	}

	/** runs iterates through two collections simultaneously, passing both args to code;
	 * e.g. a = ['a','b']; b=[1,2]; 
	 * assert ['a1','b2'] == forboth(a,b) { x,y -> x+y }
	 * 	
	 * @param l1
	 * @param l2
	 * @param code
	 * @return
	 */
	public static Collection forBoth(Collection l1, Collection l2, Closure code) {
		def result=[]
		l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i]) ) }
		result
	}
	public static Collection forBothWithIndex(Collection l1, Collection l2, Closure code) {
		def result=[]
		l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i], i) ) }
		result
	}
	public static Collection forBoth(Object[] l1, Object[] l2, Closure code) {
		def result=[]
		l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i]) ) }
		result
	}
	public static Collection forBothWithIndex(Object[] l1, Object[] l2, Closure code) {
		def result=[]
		l1.eachWithIndex { a, i -> result.add( code.call(a, l2[i], i) ) }
		result
	}

	/** return value used to indicate that there is no such field */
	public static final Object NO_SUCH_FIELD = new Object();
	/** default field getter, delegates to object[field] (which will invoke a getter if one exists, in groovy),
	 * unless field starts with "@" in which case it looks up the actual java field (bypassing getter);
	 * can be extended as needed when passed to {@link #equals(Object, Object, Class, String[])} */
	public static final Closure DEFAULT_FIELD_GETTER = { Object object, Object field ->
		try {
//			println "getting $field"
			if ((field in String) && field.startsWith("@")) {
//				println "  getting @ ${field.substring(1)}"
//				println "  got@ "+object.@"${field.substring(1)}"
				return object.@"${field.substring(1)}"
			}
//			println "  got "+object[field]
			return object[field]
		} catch (Exception e) {
//			println "  error"
//			e.printStackTrace()
			return NO_SUCH_FIELD
		}
	}
	
	/** checks equality of o1 and o2 with respect to the named fields, optionally enforcing a common superclass
	 * and using a custom field-getter. for example
	 * 
	 * <code>
	 * public class Foo {
	 *   Object bar;
	 *   public boolean equals(Object other) { LangaugeUtils.equals(this, other, Foo.class, ["bar"]); }
	 *   public int hashCode() { LangaugeUtils.hashCode(this, ["bar"]); }
	 * }
	 * </code>
	 *  
	 * @param o1 one object to compare
	 * @param o2 other object to compare
	 * 
	 * @param optionalCommonSuperClass  if supplied, returns false unless both objects are instances of the given type;
	 * 	(if not supplied it effectively does duck typing, returning false if any field is not present)
	 * 
	 * @param optionalGetter  if supplied, a closure which takes (object, field) and returns the value of field on object;
	 *  should return static {@link #NO_SUCH_FIELD} if none found;
	 *  recommended to delegate to {@link #DEFAULT_FIELD_GETTER} at least for strings (or for anything)
	 *  
	 * @param fields  typically a list of strings being names of fields on the class to compare;
	 *   other types can be supplied if they are supported by object[field] (what the {@link #DEFAULT_FIELD_GETTER} does)
	 *   or if the optionalGetter handles it;
	 *   note that object[field] causes invocation of object.getAt(field) 
	 *   (which can be provided on the object for non-strings;
	 *   this is preferred to an optionalGetter, generally),
	 *   looking for object.getXxx() (where field is a string "xxx")
	 *   then object.xxx;
	 *   one exception is that field names which start with "@" get the field directly (according to {@link #DEFAULT_FIELD_GETTER};
	 *   but use with care on private fields, as they must be on the object -- not a superclass --
	 *   and with groovy properties (formerly known as package-private, i.e. with no access modifiers)
	 *   because they become private fields)
	 *   
	 * @return true if the two objects are equal in all indicated fields, and conform to the optionalCommonSuperClass if supplied 
	 */
	public static boolean equals(Object o1, Object o2, Class<?> optionalCommonSuperClass=null, 
			Closure optionalGetter=null, Iterable<Object> fieldNames) {
		if (o1==null) return o2==null;
		if (o2==null) return false;
		if (optionalCommonSuperClass) {
			if (!(o1 in optionalCommonSuperClass) || !(o2 in optionalCommonSuperClass)) return false
		}
		Closure get = optionalGetter ?: DEFAULT_FIELD_GETTER
		for (it in fieldNames) {
			def v1 = get.call(o1, it)
			if (v1==NO_SUCH_FIELD) return false
			if (v1!=get.call(o2, it)) return false 
		}
		return true
	}
	public static boolean equals(Object o1, Object o2, Class<?> optionalCommonSuperClass=null,
			Closure optionalGetter=null, Object[] fieldNames) {
		return equals(o1, o2, optionalCommonSuperClass, optionalGetter, Arrays.asList(fieldNames) )	
	}

	/** generates a hashcode for an object, similar to com.google.common.base.Objects.hashCode(),
	 * but taking field _names_ and an optional getter, with the same rich groovy semantics as
	 * described in {@link #equals(Object, Object, Class)} (which has more extensive javadoc) */
	public static int hashCode(Object o, Closure optionalGetter=null, Collection<Object> fieldNames) {
		if (o==null) return 0;
		Closure get = optionalGetter ?: DEFAULT_FIELD_GETTER
		int result = 1;
		for (it in fieldNames) {
			def v1 = get.call(o, it)
			if (v1==NO_SUCH_FIELD) 
				throw new NoSuchFieldError("Cannot access $it on "+o.getClass());
			result = 31 * result + (it == null ? 0 : it.hashCode());
		}
		result
	}
	public static int hashCode(Object o, Closure optionalGetter=null, Object[] fieldNames) {
		hashCode(o, optionalGetter, Arrays.asList(fieldNames))
	}
	
}
