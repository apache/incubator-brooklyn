package brooklyn.rest.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.Beta;
import com.google.common.primitives.Primitives;

public class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    
    /** returns a representation of x which is guaranteed to be convertible by jersey
     * to valid json (converting toString here in any unusual cases) --
     * with the exception that strings may have to be escaped in some json contexts;
     * see comment at AbstractBrooklynRestResource#getValueForDisplay
     * <p>
     * iow, jsonable = anything which jersey methods can return and the framework 
     * will turn it in to something sensible without failing */
    public static Object toJsonable(Object x) {
        if (x==null) return null;
        // primitives and strings are simple
        if (isPrimitiveOrBoxer(x.getClass()) || x instanceof String)
            return x;
        
        return x.toString();
    }

    /** as {@link #toJsonable(Object)},
     * returns x if it can be nicely serialized by the given mapper;
     * otherwise returns toString to ensure that _a_ value is returned.
     * <p>
     * "nicely serialized" is taken to mean that an equal element is obtained
     * under after serialization and deserialization, which works for simple bean classes.
     * this is an imperfect but fairly useful measure. it may change in the future. */
    @Beta
    public static Object toJsonable(Object x, ObjectMapper mapper) {
        if (x==null) return null;
        // primitives and strings are simple
        if (isPrimitiveOrBoxer(x.getClass()) || x instanceof String)
            return x;
        try {
            boolean resultIsGood;
            String result;
            
            result = mapper.writer().writeValueAsString(x);
            Class<?> type = x.getClass();
            if (x instanceof List) type = MutableList.class;
            if (x instanceof Set) type = MutableSet.class;
            if (x instanceof Map) type = MutableMap.class;
            Object check = mapper.reader(type).readValue(result);
            resultIsGood = check.equals(x);
            
            if (resultIsGood) {
                if (log.isTraceEnabled())
                    log.trace("Confirmed ability to serialize "+x+", as "+result);
                return x;
            } else {
                if (log.isTraceEnabled())
                    log.trace("Inconsistent result serializing "+x+", as "+result+", but read in as "+check);
                return x.toString();
            }
        } catch (Throwable e) {
            if (log.isTraceEnabled())
                log.trace("Not able to serialize "+x+" ("+x.getClass()+"), returning toString: "+e);
            Exceptions.propagateIfFatal(e);
            return x.toString();
        }
    }

    public static boolean isPrimitiveOrBoxer(Class<?> type) {
        return Primitives.allPrimitiveTypes().contains(type) || Primitives.allWrapperTypes().contains(type);
    }
}
