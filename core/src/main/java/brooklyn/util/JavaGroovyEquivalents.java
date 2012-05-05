package brooklyn.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.collect.Maps;

public class JavaGroovyEquivalents {

    public static String join(Collection<?> collection, String separator) {
        StringBuffer result = new StringBuffer();
        Iterator<?> ci = collection.iterator();
        if (ci.hasNext()) result.append(asNonnullString(ci.next()));
        while (ci.hasNext()) {
            result.append(separator);
            result.append(asNonnullString(ci.next()));
        }
        return result.toString();
    }

    /** simple elvislike operators; uses groovy truth */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> elvis(Collection<T> preferred, Collection<?> fallback) {
        // TODO Would be nice to not cast, but this is groovy equivalent! Let's fix generics in stage 2
        return groovyTruth(preferred) ? preferred : (Collection<T>) fallback;
    }
    public static String elvis(String preferred, String fallback) {
        return groovyTruth(preferred) ? preferred : fallback;
    }
    public static String elvisString(Object preferred, Object fallback) {
        return elvis(asString(preferred), asString(fallback));
    }
    
    public static String asString(Object o) {
        if (o==null) return null;
        return o.toString();
    }
    public static String asNonnullString(Object o) {
        if (o==null) return "null";
        return o.toString();
    }
    
    public static boolean groovyTruth(Collection<?> c) {
        return c != null && !c.isEmpty();
    }
    public static boolean groovyTruth(String s) {
        return s != null && !s.isEmpty();
    }
    
    public static <K,V> Map<K,V> mapOf(K key1, V val1) {
        Map<K,V> result = Maps.newLinkedHashMap();
        result.put(key1, val1);
        return result;
    }
}
