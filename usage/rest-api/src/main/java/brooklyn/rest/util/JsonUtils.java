package brooklyn.rest.util;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.location.Location;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.Beta;

@Beta
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
    @Beta
    public static Object toJsonable(Object x) {
        return toJsonable(x, null);
    }

    /** returns whether an object can be nicely serialized to json;
     * the semantics of nicely serializable are subject to change */
    @Beta
    public static boolean isJsonable(Object x, @Nullable ObjectMapper mapper) {
        // optimisation, for primitives don't create hash set
        if (Jsonya.JsonPrimitiveDeepTranslator.isPrimitiveOrBoxer(x.getClass()) || x instanceof String)
            return true;
        
        JsonSerializability test = isJsonable(x, mapper, new HashSet<Object>());
        if (weakest(test, JsonSerializability.POSSIBLE)==JsonSerializability.POSSIBLE)
            return true;
        if (test==JsonSerializability.IMPOSSIBLE)
            return false;
        
        try {
            if (mapper==null)
                return false;
            
            String result = mapper.writer().writeValueAsString(x);
            return validateJsonDeserialization(result, x, mapper);
            
        } catch (OutOfMemoryError e) {
            log.warn("Memory leak trying to jsonify (returning as string): "+x);
            return false;
            
        } catch (Throwable e) {
            if (log.isTraceEnabled())
                log.trace("Not able to serialize "+x+" ("+x.getClass()+"), returning toString: "+e);
            Exceptions.propagateIfFatal(e);
            return false;
        }
    }

    /**
     * Method which tests whether json serialised json deserializes in a way which is equivalent to the given object;
     * there are some problems with this (due to varying semantics desired of "equals"), 
     * but it may be useful in determining whether to send things as toString or as JSON.
     */
    private static boolean validateJsonDeserialization(String result, Object expectedObject, @Nonnull ObjectMapper mapper) throws IOException,
        JsonProcessingException, JsonGenerationException, JsonMappingException {
        boolean resultIsGood;
        Class<?> type = expectedObject.getClass();
        if (expectedObject instanceof List) type = MutableList.class;
        if (expectedObject instanceof Set) type = MutableSet.class;
        if (expectedObject instanceof Map) type = MutableMap.class;
        Object check = mapper.reader(type).readValue(result);
        resultIsGood = check.equals(expectedObject);

        if (resultIsGood) {
            if (log.isTraceEnabled())
                log.trace("Confirmed ability to serialize "+expectedObject+", as "+result);
        } else {
            if (log.isTraceEnabled() && expectedObject instanceof Map && check instanceof Map) {
                // okay, got to the bottom of it --
                // mapper deserializes numbers in the simplest way, so if map being written contains an int-valued long,
                // maps end up treating these as not equal, with a long in one and an int the other
                String result2 = mapper.writer().writeValueAsString(check);
                log.info("MISMATCH\n"+result+"\n"+result2);
                Set<?> k1 = ((Map<?,?>)expectedObject).keySet();
                for (Object ki: k1) {
                    Object v1 = ((Map<?,?>)expectedObject).get(ki);
                    Object v2 = ((Map<?,?>)check).get(ki);
                    if (v1==null)
                        log.info("null COMPARING key "+ki);
                    else if (v1.equals(v2))
                        log.info("true COMPARING key "+ki+", value "+v1);
                    else {
                        log.info("false COMPARING key "+ki+", value "+v1+"("+v1.getClass()+") with "+v2+"("+v2.getClass()+")");
                    }
                            
                }
            }
            if (log.isTraceEnabled())
                log.trace("Inconsistent result serializing "+expectedObject+", as "+result+", but read in as "+check);
        }
        return resultIsGood;
    }
    
    /** enum indicating our confidence in being able to serialize a json object */
    private enum JsonSerializability {
        PRECISE,
        POSSIBLE,
        UNKNOWN,
        IMPOSSIBLE
    }
    
    /** returns true if it can for sure be nicely serialized by the given mapper,
     * false if it for sure cannot be, and null if it can't be sure */
    private static JsonSerializability isJsonable(Object x, @Nullable ObjectMapper mapper, Set<Object> objectsVisited) {
        if (Jsonya.JsonPrimitiveDeepTranslator.isPrimitiveOrBoxer(x.getClass()) || x instanceof String || x==null)
            return JsonSerializability.PRECISE;
        
        objectsVisited = new HashSet<Object>(objectsVisited);
        if (!objectsVisited.add(x))
            // fail if object is self-recursive
            return JsonSerializability.IMPOSSIBLE;
        
        if (x instanceof Entity || x instanceof EntityDriver || x instanceof Location)
            return JsonSerializability.IMPOSSIBLE;
        
        if (x instanceof Collection<?>) {
            JsonSerializability result = JsonSerializability.POSSIBLE;
            for (Object xi: ((Collection<?>)x)) {
                JsonSerializability jsonable = isJsonable(xi, mapper, objectsVisited);
                result = weakest(result, jsonable);
                if (result==JsonSerializability.IMPOSSIBLE) return JsonSerializability.IMPOSSIBLE;
            }
            return result;
        }
        
        if (x instanceof Map<?,?>) {
            JsonSerializability result = JsonSerializability.POSSIBLE;
            for (Map.Entry<?,?> xi: ((Map<?,?>)x).entrySet()) {
                JsonSerializability r1 = isJsonable(xi.getKey(), mapper, objectsVisited);
                result = weakest(result, r1);
                if (result==JsonSerializability.IMPOSSIBLE) return JsonSerializability.IMPOSSIBLE;
                
                JsonSerializability r2 = isJsonable(xi.getValue(), mapper, objectsVisited);
                result = weakest(result, r2);
                if (result==JsonSerializability.IMPOSSIBLE) return JsonSerializability.IMPOSSIBLE;
            }
            return result;
        }
        
        return JsonSerializability.UNKNOWN;
    }
    
    private static JsonSerializability weakest(JsonSerializability r1, JsonSerializability r2) {
        if (r1==null || r2==null) return null;
        if (r1==JsonSerializability.IMPOSSIBLE || r2==JsonSerializability.IMPOSSIBLE) return JsonSerializability.IMPOSSIBLE;
        if (r1==JsonSerializability.UNKNOWN || r2==JsonSerializability.UNKNOWN) return JsonSerializability.UNKNOWN;
        if (r1==JsonSerializability.POSSIBLE || r2==JsonSerializability.POSSIBLE) return JsonSerializability.POSSIBLE;
        if (r1==JsonSerializability.PRECISE || r2==JsonSerializability.PRECISE) return JsonSerializability.PRECISE;
        throw new IllegalStateException("Unknown serializabilities "+r1+" and "+r2);
    }

    /** as {@link #toJsonable(Object)} but uses more sophisticated {@link #isJsonable(Object, ObjectMapper)} test */
    @Beta
    public static Object toJsonable(Object x, @Nullable ObjectMapper mapper) {
        if (isJsonable(x, mapper))
            return x;
        return x.toString();
    }

}
