package brooklyn.rest.util;

import brooklyn.util.flags.TypeCoercions;

public class JsonUtils {

    /** returns a representation of x which can be serialized as a json object */
    public static Object toJsonable(Object x) {
        if (x==null) return null;
        // primitives and strings are simple
        if (TypeCoercions.isPrimitiveOrBoxer(x.getClass()) || x instanceof String)
            return x;
        
        // TODO plug in to other serialization techniques
        return x.toString();
    }
    
}
