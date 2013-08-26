package brooklyn.util.javalang;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Reflection utilities ("borrowed" from cloudsoft monterey).
 * 
 * @author aled
 */
public class Reflections {

    private static final Logger LOG = LoggerFactory.getLogger(Reflections.class);

	public static class ReflectionNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 9032835250796708037L;
		public ReflectionNotFoundException(String message, Throwable cause) {
			super(message, cause);
		}
		public ReflectionNotFoundException(String message) {
			super(message);
		}
	}

	public static class ReflectionAccessException extends RuntimeException {
		private static final long serialVersionUID = 6569605861192432009L;

		public ReflectionAccessException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final ClassLoader classLoader;
	
	public Reflections(ClassLoader classLoader) {
		this.classLoader = checkNotNull(classLoader);
	}

	public Object loadInstance(String classname, Class<?>[] argTypes, Object[] argValues) throws ReflectionNotFoundException, ReflectionAccessException {
		Class<?> clazz = loadClass(classname);
		Constructor<?> constructor = loadConstructor(clazz, argTypes);
		return loadInstance(constructor, argValues);
	}

	public Object loadInstance(String classname) throws ReflectionNotFoundException, ReflectionAccessException {
		Class<?> clazz = loadClass(classname);
		try {
			return clazz.newInstance();
		} catch (InstantiationException e) {
			throw new ReflectionAccessException("Failed to create instance of class '" + classname + "' using class loader " + classLoader, e);
		} catch (IllegalAccessException e) {
			throw new ReflectionAccessException("Failed to create instance of class '" + classname + "' using class loader " + classLoader, e);
		}
	}

	/** instantiates the given class from its binary name */
	public Class<?> loadClass(String classname) throws ReflectionNotFoundException {
		try {
			return classLoader.loadClass(classname);
		} catch (ClassNotFoundException e) {
			throw new ReflectionNotFoundException("Failed to load class '" + classname + "' using class loader " + classLoader, e);
		} catch (NoClassDefFoundError e) {
			throw new ReflectionNotFoundException("Failed to load class '" + classname + "' using class loader " + classLoader, e);
		} catch (UnsupportedClassVersionError e) {
			throw new ReflectionNotFoundException("Failed to load class '" + classname + "' using class loader " + classLoader, e);
		}
	}

	/** given a nested part, e.g. Inner$VeryInner, this will recurse through clazz.Inner, looking for VeryInner,
	 * then looking in each supertype (interface) of clazz for Inner.VeryInner;
	 * <p>
	 * so it will find Clazz.Inner.VeryInner wherever in the hierarchy it is defined
	 * <p>
	 * (as opposed to ClassLoader which requires Inner.VeryInner to be _declared_ in clazz, not in any supertype
	 * <p>
	 * returns null if not found
	 */
	public static Class<?> loadInnerClassPossiblyInheritted(Class<?> clazz, String nestedPart) throws ReflectionNotFoundException {
		Set<String> visited = new HashSet<String>();
		Class<?> result = loadInnerClassPossiblyInheritted(visited, clazz, nestedPart);
		if (result!=null) return result;
		throw new ReflectionNotFoundException("Inner class " + nestedPart + " could not be found in " + clazz + " or any of its super-types");
	}
	
	/** as 2-arg, but maintains set of  visited elements, and returns null if not found */
	private static Class<?> loadInnerClassPossiblyInheritted(Set<String> visited, Class<?> clazz, String nestedPart) throws ReflectionNotFoundException {
		if (clazz==null) return null;
		if (nestedPart==null || nestedPart.length()==0) return clazz;

		int i1 = nestedPart.indexOf('$');
		int i2 = nestedPart.indexOf('.');
		int idx = (i2 > -1 && (i2 < i1 || i1==-1) ? i2 : i1);
		String thisClassToFind = nestedPart;
		String nextClassesToFind = "";
		if (idx>=0) {
			thisClassToFind = nestedPart.substring(0, idx);
			nextClassesToFind = nestedPart.substring(idx+1);
		}

		if (!visited.add(clazz.getCanonicalName()+"!"+nestedPart)) {
			//already visited
			return null;
		}

		Class<?>[] members = clazz.getClasses();
		for (int i = 0; i < members.length; i++) {
			if (members[i].getSimpleName().equals(thisClassToFind)) {
				Class<?> clazzI = loadInnerClassPossiblyInheritted(visited, members[i], nextClassesToFind);
				if (clazzI!=null) return clazzI;
			}
		}

		//look in supertype first (not sure if necessary)
		Class<?> result = loadInnerClassPossiblyInheritted(visited, clazz.getSuperclass(), nestedPart);
		if (result!=null) return result;

		for (Class<?> iface : clazz.getInterfaces()) {
			result = loadInnerClassPossiblyInheritted(visited, iface, nestedPart);
			if (result!=null) return result;
		}
		return null;
	}

	/** does not look through ancestors of outer class */
	public Class<?> loadInnerClassNotInheritted(String outerClassname, String innerClassname) throws ReflectionNotFoundException {
		return loadClass(outerClassname + "$" + innerClassname);
	}

	/** does not look through ancestors of outer class
	 * <p>
	 * uses the classloader set in this class, not in the clazz supplied */
	public Class<?> loadInnerClassNotInheritted(Class<?> outerClazz, String innerClassname) throws ReflectionNotFoundException {
		return loadClass(outerClazz.getName() + "$" + innerClassname);
	}

	public Constructor<?> loadConstructor(Class<?> clazz, Class<?>[] argTypes) throws ReflectionAccessException {
		try {
			return clazz.getConstructor(argTypes);
		} catch (SecurityException e) {
			throw new ReflectionAccessException("Failed to load constructor of class '" + clazz + " with argument types " + Arrays.asList(argTypes), e);
		} catch (NoSuchMethodException e) {
			throw new ReflectionAccessException("Failed to load constructor of class '" + clazz + " with argument types " + Arrays.asList(argTypes), e);
		}
	}

	/**
	 * Returns a constructor that accepts the given arguments, or null if no such constructor is
	 * accessible.
	 */
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> findCallabaleConstructor(Class<T> clazz, Object[] args) {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = (args[i] != null) ? args[i].getClass() : null;
        }
        
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (isCallableConstructor(constructor, argTypes)) {
                return (Constructor<T>) constructor;
            }
        }
        return null;
    }

	private static boolean isCallableConstructor(Constructor<?> constructor, Class<?>[] argTypes) {
	    Class<?>[] expectedTypes = constructor.getParameterTypes();
	    if (expectedTypes.length != argTypes.length) return false;
	    
	    for (int i = 0; i < argTypes.length; i++) {
	        if (argTypes[i] == null) {
	            // null usable anywhere
	            continue;
	        } else if (expectedTypes[i].isAssignableFrom(argTypes[i])) {
	            continue;
	        } else {
	            // TODO auto-boxing
	            return false;
	        }
	    }
        return true;
    }

    /** returns a single constructor in a given class, or throws an exception */
	public Constructor<?> loadSingleConstructor(Class<?> clazz) {
		Constructor<?>[] constructors = clazz.getConstructors();
		if (constructors.length == 1) {
			return constructors[0];
		}
		throw new IllegalArgumentException("Class " + clazz + " has more than one constructor");
	}

	public <T> T loadInstance(Constructor<T> constructor, Object[] argValues) throws IllegalArgumentException, ReflectionAccessException {
		try {
			try {
				return constructor.newInstance(argValues);
			} catch (IllegalArgumentException e) {
				try {
					LOG.warn("Failure passing provided arguments ("+getIllegalArgumentsErrorMessage(constructor, argValues)+"; "+e+"); attempting to reconstitute");
					argValues = (Object[]) updateFromNewClassLoader(argValues);
					return constructor.newInstance(argValues);
				} catch (Throwable e2) {
					LOG.warn("Reconstitution attempt failed (will rethrow original excaption): "+e2, e2);
					throw e;
				}
			}
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(getIllegalArgumentsErrorMessage(constructor, argValues), e);
		} catch (InstantiationException e) {
			throw new ReflectionAccessException("Failed to create instance of '" + constructor.getDeclaringClass(), e);
		} catch (IllegalAccessException e) {
			throw new ReflectionAccessException("Failed to create instance of '" + constructor.getDeclaringClass(), e);
		} catch (InvocationTargetException e) {
			throw new ReflectionAccessException("Failed to create instance of '" + constructor.getDeclaringClass(), e);
		}
	}

	public Method loadMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) throws ReflectionNotFoundException, ReflectionAccessException {
		try {
			return clazz.getMethod(methodName, argTypes);
		} catch (NoClassDefFoundError e) {
			throw new ReflectionNotFoundException("Failed to invoke method '" + methodName + " on class " + clazz + " with argument types " + Arrays.asList(argTypes) + ", using class loader " + clazz.getClassLoader(), e);
		} catch (NoSuchMethodException e) {
			throw new ReflectionNotFoundException("Failed to invoke method '" + methodName + " on class " + clazz + " with argument types " + Arrays.asList(argTypes), e);
		} catch (SecurityException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + methodName + " on class " + clazz + " with argument types " + Arrays.asList(argTypes), e);
		}
	}

	/** returns the first method matching the given name */
	public Method loadMethod(Class<?> clazz, String methodName) throws ReflectionNotFoundException, ReflectionAccessException {
		try {
			Method[] allmethods = clazz.getMethods();
			for (int i = 0; i < allmethods.length; i++) {
				if (allmethods[i].getName().equals(methodName)) {
					return allmethods[i];
				}
			}
			throw new ReflectionNotFoundException("Cannot find method " + methodName + " on class " + clazz);

		} catch (SecurityException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + methodName + " on class " + clazz, e);
		}
	}

	/**
	 * 
	 * @throws ReflectionAccessException If invocation failed due to illegal access or the invoked method failed
	 * @throws IllegalArgumentException  If the arguments were invalid
	 */
	public Object invokeMethod(Method method, Object obj, Object... argValues) throws ReflectionAccessException {
		try {
			return method.invoke(obj, argValues);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(getIllegalArgumentsErrorMessage(method, argValues), e);
		} catch (IllegalAccessException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + method.toGenericString() + " on class " + method.getDeclaringClass() + " with argument values " + Arrays.asList(argValues), e);
		} catch (InvocationTargetException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + method.toGenericString() + " on class " + method.getDeclaringClass() + " with argument values " + Arrays.asList(argValues), e);
		}
	}

	public Object invokeStaticMethod(Method method, Object... argValues) throws IllegalArgumentException, ReflectionAccessException {
		try {
			return method.invoke(null, argValues);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(getIllegalArgumentsErrorMessage(method, argValues), e);
		} catch (IllegalAccessException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + method.toGenericString() + " on class " + method.getDeclaringClass() + " with argument values " + Arrays.asList(argValues), e);
		} catch (InvocationTargetException e) {
			throw new ReflectionAccessException("Failed to invoke method '" + method.toGenericString() + " on class " + method.getDeclaringClass() + " with argument values " + Arrays.asList(argValues), e);
		}
	}

	public Object loadStaticField(Class<?> clazz, String fieldname) throws ReflectionAccessException {
		return loadStaticFields(clazz, new String[] {fieldname}, null)[0];
	}

	public Object[] loadStaticFields(Class<?> clazz, String[] fieldnamesArray, Object[] defaults) throws ReflectionAccessException {
		Object[] result = new Object[fieldnamesArray.length];
		if (defaults!=null) {
			for (int i = 0; i < defaults.length; i++) {
				result[i] = defaults[i];
			}
		}

		List<String> fieldnames = Arrays.asList(fieldnamesArray);
		Field[] classFields = clazz.getDeclaredFields();

		for (int i = 0; i < classFields.length; i++) {
			Field field = classFields[i];
			int index = fieldnames.indexOf(field.getName());
			if (index >= 0) {
				try {
					result[index] = field.get(null);
				} catch (IllegalArgumentException e) {
					throw new ReflectionAccessException("Failed to load field '" + field.getName() + " from class " + clazz, e);
				} catch (IllegalAccessException e) {
					throw new ReflectionAccessException("Failed to load field '" + field.getName() + " from class " + clazz, e);
				}
			}
		}
		return result;
	}

	private static String getIllegalArgumentsErrorMessage(Method method, Object[] argValues) {
		return method.toGenericString() + " not applicable for the parameters of type " + argumentTypesToString(argValues);
	}

	private static String getIllegalArgumentsErrorMessage(Constructor<?> constructor, Object[] argValues) {
		return constructor.toGenericString() + " not applicable for the parameters of type " + argumentTypesToString(argValues);
	}

	private static String argumentTypesToString(Object[] argValues) {
		StringBuffer msg = new StringBuffer("(");
		for (int i = 0; i < argValues.length; i++) {
			if (i != 0) msg.append(", ");
			msg.append(argValues[i] != null ? argValues[i].getClass().getName() : "null");
		}
		msg.append(")");
		return msg.toString();
	}

	/** copies all fields from the source to target; very little compile-time safety checking, so use with care
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException */
	public static <T> void copyFields(T source, T target) throws IllegalArgumentException, IllegalAccessException {
		Class<? extends Object> clazz = source.getClass();
		while (clazz!=null) {
			Field[] fields = clazz.getDeclaredFields();
			for (Field f : fields) {
				f.setAccessible(true);
				Object vs = f.get(source);
				Object vt = f.get(target);
				if ((vs==null && vt!=null) || (vs!=null && !vs.equals(vt))) {
					f.set(target, vs);
				}
			}
			clazz = clazz.getSuperclass();
		}
	}

	/**
	 * Loads class given its canonical name format (e.g. com.acme.Foo.Inner),
	 * using iterative strategy (trying com.acme.Foo$Inner, then com.acme$Foo$Inner, etc).
	 * @throws ReflectionNotFoundException 
	 */
	public Class<?> loadClassFromCanonicalName(String canonicalName) throws ClassNotFoundException, ReflectionNotFoundException {
		ClassNotFoundException err = null;
		String name = canonicalName;
		do {
			try {
				return classLoader.loadClass(name);
			} catch (ClassNotFoundException e) {
				if (err == null) err = e;
				int lastIndexOf = name.lastIndexOf(".");
				if (lastIndexOf >= 0) {
					name = name.substring(0, lastIndexOf) + "$" + name.substring(lastIndexOf+1);
				}
			}
		} while (name.contains("."));
		throw err;
	}

	/** finds the resource in the classloader, if it exists; inserts or replaces leading slash as necessary
	 * (i believe it should _not_ have one, but there is some inconsistency)
	 * 
	 * Will return null if no resource is found.
	 */
	@Nullable
	public URL getResource(String r) {
		URL u = null;
		u = classLoader.getResource(r);
		if (u!=null) return u;
		
		if (r.startsWith("/")) r = r.substring(1);
		else r = "/"+r;
		return classLoader.getResource(r);
	}

	/**
	 * Serialize the given object, then reload using the current class loader;
	 * this removes linkages to instances with classes loaded by an older class loader.
	 * <p>
	 * (like a poor man's clone)
	 * <p>
	 * aka "reconstitute(Object)"
	 */
	public final Object updateFromNewClassLoader(Object data) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new ObjectOutputStream(bytes).writeObject(data);
		Object reconstituted = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
		if (LOG.isDebugEnabled()) LOG.debug("Reconstituted data: " + reconstituted + ", class loader: " + classLoader);
		return reconstituted;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}
	
	// FIXME I thought this method already existed somewhere? If so, remove duplication. 
	public static StackTraceElement getCaller() {
	    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
	    // 0 is Thread.getStackTrace()
	    // 1 is ReflectionUtils.getCaller()
	    // 2 is caller of getCaller();
	    // 3 is caller of method that requested the caller
	    return stackTrace[3];
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Class<? super T> findSuperType(T impl, String typeName) {
        Set<Class<?>> toinspect = new LinkedHashSet<Class<?>>();
	    Set<Class<?>> inspected = new HashSet<Class<?>>();
	    toinspect.add(impl.getClass());
	    
	    while (toinspect.size() > 0) {
	        Class<?> clazz = toinspect.iterator().next(); // get and remove the first element
	        if (clazz.getName().equals(typeName)) {
	            return (Class<? super T>) clazz;
	        }
	        inspected.add(clazz);
	        List<Class<?>> toAdd = Arrays.asList(clazz.getInterfaces());
	        toinspect.addAll( toAdd );
	        if (clazz.getSuperclass() != null) toinspect.add(clazz.getSuperclass());
	        toinspect.removeAll(inspected);
	    }
	    
	    return null;
	}
	
	/** whereas Class.getInterfaces() only returns interfaces directly implemented by a class,
	 * this walks the inheritance hierarchy to include interfaces implemented by superclass/ancestors;
	 * (note it does not include superinterfaces)
	 */
	public static Set<Class<?>> getInterfacesIncludingClassAncestors(Class<?> clazz) {
	    Set<Class<?>> result = new LinkedHashSet<Class<?>>();
	    while (clazz!=null) {
	        for (Class<?> iface: clazz.getInterfaces())
	            result.add(iface);
	        clazz = clazz.getSuperclass();
	    }
	    return result;
	}

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        if (clazz == null || name == null) {
            throw new NullPointerException("Must not be null: clazz="+clazz+"; name="+name);
        }
        Class<?> clazzToInspect = clazz;
        NoSuchMethodException toThrowIfFails = null;
        
        while (clazzToInspect != null) {
            try {
                return clazzToInspect.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                if (toThrowIfFails == null) toThrowIfFails = e;
                clazzToInspect = clazzToInspect.getSuperclass();
            }
        }
        throw toThrowIfFails;
    }
    
    public static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        if (clazz == null || name == null) {
            throw new NullPointerException("Must not be null: clazz="+clazz+"; name="+name);
        }
        Class<?> clazzToInspect = clazz;
        NoSuchFieldException toThrowIfFails = null;
        
        while (clazzToInspect != null) {
            try {
                return clazzToInspect.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                if (toThrowIfFails == null) toThrowIfFails = e;
                clazzToInspect = clazzToInspect.getSuperclass();
            }
        }
        throw toThrowIfFails;
    }
    
    public static List<Field> findPublicFieldsOrderedBySuper(Class<?> clazz) {
        checkNotNull(clazz, "clazz");
        MutableList.Builder<Field> result = MutableList.<Field>builder();
        Stack<Class<?>> tovisit = new Stack<Class<?>>();
        Set<Class<?>> visited = Sets.newLinkedHashSet();
        tovisit.push(clazz);
        
        while (!tovisit.isEmpty()) {
            Class<?> nextclazz = tovisit.pop();
            if (!visited.add(nextclazz)) {
                continue; // already visited
            }
            if (nextclazz.getSuperclass() != null) tovisit.add(nextclazz.getSuperclass());
            tovisit.addAll(Arrays.asList(nextclazz.getInterfaces()));
            
            result.addAll(Iterables.filter(Arrays.asList(nextclazz.getDeclaredFields()), new Predicate<Field>() {
                @Override public boolean apply(Field input) {
                    return Modifier.isPublic(input.getModifiers());
                }}));
            
        }
        
        List<Field> resultList = result.build();
        Collections.sort(resultList, new Comparator<Field>() {
            @Override public int compare(Field f1, Field f2) {
                Field fsubbest = inferSubbestField(f1, f2);
                return (fsubbest == null) ? 0 : (fsubbest == f1 ? 1 : -1);
            }});
        
        return resultList;
    }
    
    // TODO I've seen strange behaviour where class.getMethods() does not include methods from interfaces.
    // Also the ordering guarantees here are useful...
    public static List<Method> findPublicMethodsOrderedBySuper(Class<?> clazz) {
        checkNotNull(clazz, "clazz");
        MutableList.Builder<Method> result = MutableList.<Method>builder();
        Stack<Class<?>> tovisit = new Stack<Class<?>>();
        Set<Class<?>> visited = Sets.newLinkedHashSet();
        tovisit.push(clazz);
        
        while (!tovisit.isEmpty()) {
            Class<?> nextclazz = tovisit.pop();
            if (!visited.add(nextclazz)) {
                continue; // already visited
            }
            if (nextclazz.getSuperclass() != null) tovisit.add(nextclazz.getSuperclass());
            tovisit.addAll(Arrays.asList(nextclazz.getInterfaces()));
            
            result.addAll(Iterables.filter(Arrays.asList(nextclazz.getDeclaredMethods()), new Predicate<Method>() {
                @Override public boolean apply(Method input) {
                    return Modifier.isPublic(input.getModifiers());
                }}));
            
        }
        
        List<Method> resultList = result.build();
        Collections.sort(resultList, new Comparator<Method>() {
            @Override public int compare(Method m1, Method m2) {
                Method msubbest = inferSubbestMethod(m1, m2);
                return (msubbest == null) ? 0 : (msubbest == m1 ? 1 : -1);
            }});
        
        return resultList;
    }
    
    /**
     * Gets the field that is in the sub-class; or null if one field does not come from a sub-class of the other field's class
     */
    public static Field inferSubbestField(Field f1, Field f2) {
        Class<?> c1 = f1.getDeclaringClass();
        Class<?> c2 = f2.getDeclaringClass();
        boolean isSuper1 = c1.isAssignableFrom(c2);
        boolean isSuper2 = c2.isAssignableFrom(c1);
        return (isSuper1) ? (isSuper2 ? null : f2) : (isSuper2 ? f1 : null);
    }
    
    /**
     * Gets the method that is in the sub-class; or null if one method does not come from a sub-class of the other method's class
     */
    public static Method inferSubbestMethod(Method m1, Method m2) {
        Class<?> c1 = m1.getDeclaringClass();
        Class<?> c2 = m2.getDeclaringClass();
        boolean isSuper1 = c1.isAssignableFrom(c2);
        boolean isSuper2 = c2.isAssignableFrom(c1);
        return (isSuper1) ? (isSuper2 ? null : m2) : (isSuper2 ? m1 : null);
    }
    
    /**
     * Gets the class that is in the sub-class; or null if neither is a sub-class of the other.
     */
    public static Class<?> inferSubbest(Class<?> c1, Class<?> c2) {
        boolean isSuper1 = c1.isAssignableFrom(c2);
        boolean isSuper2 = c2.isAssignableFrom(c1);
        return (isSuper1) ? (isSuper2 ? null : c2) : (isSuper2 ? c1 : null);
    }
    
    /** convenience for casting the given candidate to the given type (without any coercion, and allowing candidate to be null) */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object candidate, Class<? extends T> type) {
        if (candidate==null) return null;
        if (!type.isAssignableFrom(candidate.getClass()))
            throw new IllegalArgumentException("Requires a "+type+", but had a "+candidate.getClass()+" ("+candidate+")");
        return (T)candidate;
    }
}
