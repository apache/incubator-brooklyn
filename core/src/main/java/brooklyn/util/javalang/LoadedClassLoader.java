package brooklyn.util.javalang;

import java.util.LinkedHashMap;
import java.util.Map;

/** a classloader which allows you to register classes and resources which this loader will return when needed,
 * (essentially a registry rather than a classloader, but useful if you need to make new classes available in
 * an old context) */
public class LoadedClassLoader extends ClassLoader {

    Map<String, Class<?>> loadedClasses = new LinkedHashMap<String, Class<?>>();
    
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = loadedClasses.get(name);
        if (result==null) throw new ClassNotFoundException(""+name+" not known here");
        return result;
    }

    public void addClass(Class<?> clazz) {
        loadedClasses.put(clazz.getName(), clazz);
    }
    
    // TODO could also add resources
    
}
