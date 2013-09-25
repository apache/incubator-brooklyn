package brooklyn.util.javalang;

import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;

public class JavaClassNames {

    private static final StackTraceSimplifier STACK_TRACE_SIMPLIFIER_EXCLUDING_UTIL_JAVALANG = 
            StackTraceSimplifier.newInstance(StackTraceSimplifier.class.getPackage().getName()+".");
    
    /** returns the Class of anything which isn't a class; if input is class it is pass-through */
    public static Class<?> type(Object x) {
        if (x==null) return null;
        if (x instanceof Class) return (Class<?>)x;
        if (x instanceof TypeToken) return ((TypeToken<?>)x).getRawType();
        return x.getClass();
    }

    /** like type, but removes any array modifiers */
    public static Class<?> componentType(Object x) {
        Class<?> c = type(x);
        if (c==null) return null;
        while (c.isArray()) {
            c = c.getComponentType();
        }
        return c;
    }

    /**  returns a simplified name of the class, just the simple name if it seems useful, else the full name */
    public static String simpleClassName(Class<?> t) {
        int arrayCount = 0;
        while (t.isArray()) {
            arrayCount++;
            t = t.getComponentType();
        }
        Class<?> ct = componentType(t);
        
        String result = ct.getSimpleName();
        if (Strings.isBlank(result) || result.length()<=4) {
            if (ct.isPrimitive()) {
                // TODO unbox
            } else {
                result = ct.getName();
            }
        }
        return result+Strings.repeat("[]", arrayCount);
    }
    
    /** as {@link #simpleClassName(Class)} but taking the type of the object if it is not already a class
     * or a type-token; callers should usually do the getClass themselves, unless they aren't sure whether
     * it is already a Class-type object */
    public static String simpleClassName(Object x) {
        if (x==null) return null;
        return simpleClassName(type(x));
    }

    /** as {@link #simpleClassName(Class)} but taking a string rep'n of the class name,
     * and doing best effort to simplify it (without instantiating) */
    public static String simplifyClassName(String className) {
        if (className==null) return null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot < className.length()-5)
            return className.substring(lastDot+1);
        return className;
    }
 
    public static String packageName(Object x) {
        return componentType(x).getPackage().getName();
    }

    /** returns e.g. "/com/acme/" for an object in package com.acme */ 
    public static String packagePath(Object x) {
        return Urls.mergePaths("/", componentType(x).getPackage().getName().replace('.', '/'), "/");
    }

    /** returns path relative to the package of x, unless path is absolute.
     * useful to mimic Class.getResource(path) behaviour, cf Class.resolveName where the first argument below is the class. */
    public static String resolveName(Object context, String path) {
        Preconditions.checkNotNull(path, "path must not be null");
        if (path.startsWith("/") || Urls.isUrlWithProtocol(path)) return path;
        Preconditions.checkNotNull(context, "context must not be null when path is relative");
        return packagePath(context)+path;
    }

    /** returns a "classpath:" URL given a context object and a file to be found in that directory or a sub-directory
     * (ignoring the context object if the given path is absolute, i.e. starting with "/" or "protocol:") 
     * e.g. "classpath://com/acme/foo.txt" given a context object com.acme.SomeClass and "foo.txt" */
    public static String resolveClasspathUrl(Object context, String path) {
        if (Urls.isUrlWithProtocol(path)) return path;
        // additional / comes from resolve name
        return "classpath:/"+resolveName(context, path);
    }

    /** returns a cleaned stack trace; caller is usually at the top */
    public static StackTraceElement[] currentStackTraceCleaned() {
        return STACK_TRACE_SIMPLIFIER_EXCLUDING_UTIL_JAVALANG.clean(
                Thread.currentThread().getStackTrace());
    }
    
    /** returns top of cleaned stack trace; usually the caller's location */
    public static StackTraceElement currentStackElement() {
        return STACK_TRACE_SIMPLIFIER_EXCLUDING_UTIL_JAVALANG.nthUseful(0,
                Thread.currentThread().getStackTrace());
    }

    /** returns element in cleaned stack trace; usually the caller's location is at the top,
     * and caller of that is up one, etc */
    public static StackTraceElement callerStackElement(int depth) {
        return STACK_TRACE_SIMPLIFIER_EXCLUDING_UTIL_JAVALANG.nthUseful(depth,
                Thread.currentThread().getStackTrace());
    }

    /** returns nice class name and method for the given element */
    public static String niceClassAndMethod(StackTraceElement st) {
        return simplifyClassName(st.getClassName())+"."+st.getMethodName();
    }

    /** returns nice class name and method for the caller, going up the stack (filtered to remove invocation etc),
     * with 0 typically being the context where this method is called, 1 being its caller, etc */
    public static String callerNiceClassAndMethod(int depth) {
        return niceClassAndMethod(callerStackElement(depth));
    }

    /** convenience for {@link #callerNiceClassAndMethod(int)} with depth 0
     * <p>
     * useful for tests and other debug-facing log messages! */
    public static String niceClassAndMethod() {
        return callerNiceClassAndMethod(0);
    }
    
}
