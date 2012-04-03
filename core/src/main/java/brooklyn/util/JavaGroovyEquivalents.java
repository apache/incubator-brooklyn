package brooklyn.util;

import java.util.Collection;
import java.util.Iterator;

public class JavaGroovyEquivalents {

    public static String join(Collection collection, String separator) {
        StringBuffer result = new StringBuffer();
        Iterator ci = collection.iterator();
        if (ci.hasNext()) result.append(asNonnullString(ci.next()));
        while (ci.hasNext()) {
            result.append(separator);
            result.append(asNonnullString(ci.next()));
        }
        return result.toString();
    }

    /** simple elvislike operator for strings; if it is null or empty */
    public static String elvis(String preferred, String fallback) {
        return preferred!=null && preferred.length()>0 ? preferred : fallback;
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
    
}
