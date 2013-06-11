package brooklyn.util.javalang;

import com.google.common.base.Preconditions;

import brooklyn.util.net.Urls;

public class JavaClassNames {

    /** returns the Class of anything which isn't a class; if input is class it is pass-through */
    public static Class<?> type(Object x) {
        Preconditions.checkNotNull(x, "type must not be null");
        if (!(x instanceof Class)) return x.getClass();
        return (Class<?>)x;
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

    public static String simpleClassName(Object x) {
        Class<?> c = type(x);
        Class<?> ct = componentType(c);
        // TODO more logic and tests
        return ct.getSimpleName();
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
}
