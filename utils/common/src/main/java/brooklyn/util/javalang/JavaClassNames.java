package brooklyn.util.javalang;

import brooklyn.util.net.Urls;

public class JavaClassNames {

    /** returns the Class of anything which isn't a class; if input is class it is pass-through */
    public static Class<?> type(Object x) {
        if (x==null) return null;
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
    public static String resolveName(Object x, String path) {
        if (path==null || path.startsWith("/")) return path;
        return packagePath(x)+path;
    }

}
