package brooklyn.util.internal

import java.lang.reflect.Modifier
import java.util.Collection
import java.util.Map

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
	
	@SuppressWarnings("unchecked")
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
	
}
