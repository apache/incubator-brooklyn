package brooklyn.entity.rebind;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Helpers for transforming references to locations into location ids, and vice versa.
 * This is used when serializing a location, and when re-binding that location, when the 
 * location holds references to other locations.
 * 
 * TODO This is unpleasant code. It is required because there is not a clean distinction
 * between object construction and object configuration. Therefore, some locations expect
 * to be passed (in their constructor for SetFromFlag) references to other locations.
 * That makes it hard to serialize a set of inter-connected locations, and subsequently
 * rebind them.
 * 
 * TODO Has limited support for transforms - fields that are iterables/maps must be declared 
 * of type List, Set, Collection, Iterable or Map.
 * 
 * @author aled
 */
public class MementoTransformer {

    public static <T> T transformIdsToLocations(RebindContext rebindContext, Object value, Class<T> requiredType) {
        if (Location.class.isAssignableFrom(requiredType)) {
            return (T) rebindContext.getLocation((String)value);
            
        } else if (Set.class.isAssignableFrom(requiredType)) {
            Set<Location> result = Sets.newLinkedHashSet();
            for (String id : (Iterable<String>)value) {
                result.add(rebindContext.getLocation(id));
            }
            return (T) result;
            
        } else if (List.class.isAssignableFrom(requiredType) || Collection.class.isAssignableFrom(requiredType) || Iterable.class.isAssignableFrom(requiredType)) {
            List<Location> result = Lists.newArrayList();
            for (String id : (Iterable<String>)value) {
                result.add(rebindContext.getLocation(id));
            }
            return (T) result;
            
        } else if (value instanceof Map) {
            Map<Object,Location> result = Maps.newLinkedHashMap();
            for (Map.Entry<?, String> entry : ((Map<?,String>)value).entrySet()) {
                result.put(entry.getKey(), rebindContext.getLocation(entry.getValue()));
            }
            return (T) result;
            
        } else {
            throw new IllegalStateException("Cannot transform ids to locations of type "+requiredType);
        }
    }
    
    public static <T> T transformIdsToEntities(RebindContext rebindContext, Object value, Class<T> requiredType) {
        if (Entity.class.isAssignableFrom(requiredType)) {
            return (T) rebindContext.getEntity((String)value);
            
        } else if (Set.class.isAssignableFrom(requiredType)) {
            Set<Entity> result = Sets.newLinkedHashSet();
            for (String id : (Iterable<String>)value) {
                result.add(rebindContext.getEntity(id));
            }
            return (T) result;
            
        } else if (List.class.isAssignableFrom(requiredType) || Collection.class.isAssignableFrom(requiredType) || Iterable.class.isAssignableFrom(requiredType)) {
            List<Entity> result = Lists.newArrayList();
            for (String id : (Iterable<String>)value) {
                result.add(rebindContext.getEntity(id));
            }
            return (T) result;
            
        } else if (value instanceof Map) {
            Map<Object,Entity> result = Maps.newLinkedHashMap();
            for (Map.Entry<?, String> entry : ((Map<?,String>)value).entrySet()) {
                result.put(entry.getKey(), rebindContext.getEntity(entry.getValue()));
            }
            return (T) result;
            
        } else {
            throw new IllegalStateException("Cannot transform ids to entities of type "+requiredType);
        }
    }
    
	public static Object transformLocationsToIds(Object value) {
		if (value instanceof Location) {
			return ((Location)value).getId();
		} else if (value instanceof Iterable) {
			return transformLocationsToIds((Iterable<?>)value);
		} else if (value instanceof Map) {
			return transformLocationsToIds((Map<?,?>)value);
		} else {
			return value;
		}
	}

    public static Object transformEntitiesToIds(Object value) {
        if (value instanceof Entity) {
            return ((Entity)value).getId();
        } else if (value instanceof Iterable) {
            return transformEntitiesToIds((Iterable<?>)value);
        } else if (value instanceof Map) {
            return transformEntitiesToIds((Map<?,?>)value);
        } else {
            return value;
        }
    }

    private static Iterable<?> transformLocationsToIds(Iterable<?> vs) {
    	if (containsMatch(vs, Predicates.instanceOf(Location.class))) {
    		// refers to other locations; must be transformed
    		// assumes is entirely composed of location objects
    		List<String> result = Lists.newArrayList();
    		for (Object v : vs) {
    			result.add(((Location)v).getId());
    		}
    		return result;
    	} else {
    		return vs;
    	}
	}

    private static Map<?,?> transformLocationsToIds(Map<?,?> vs) {
    	if (containsMatch(vs.values(), Predicates.instanceOf(Location.class))) {
    		// requires transforming for serialization
    		Map<Object,Object> result = Maps.newLinkedHashMap();
    		for (Map.Entry<?,?> entry : vs.entrySet()) {
    			Object k = entry.getKey();
    			Object v = entry.getValue();
    			result.put(k, ((Location)v).getId());
    		}
    		return result;
    	} else {
    		return vs;
    	}
	}
    
    private static Iterable<?> transformEntitiesToIds(Iterable<?> vs) {
        if (containsMatch(vs, Predicates.instanceOf(Entity.class))) {
            // refers to other Entities; must be transformed
            // assumes is entirely composed of Entity objects
            List<String> result = Lists.newArrayList();
            for (Object v : vs) {
                result.add(((Entity)v).getId());
            }
            return result;
        } else {
            return vs;
        }
    }

    private static Map<?,?> transformEntitiesToIds(Map<?,?> vs) {
        if (containsMatch(vs.values(), Predicates.instanceOf(Entity.class))) {
            // requires transforming for serialization
            Map<Object,Object> result = Maps.newLinkedHashMap();
            for (Map.Entry<?,?> entry : vs.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                result.put(k, ((Entity)v).getId());
            }
            return result;
        } else {
            return vs;
        }
    }

    private static <T> boolean containsMatch(Iterable<T> vals, Predicate<? super T> predicate) {
    	for (T val : vals) {
    		if (predicate.apply(val)) {
    			return true;
    		}
    	}
    	return false;
    }
}
