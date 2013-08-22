package brooklyn.entity.rebind;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

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

    private static final Logger LOG = LoggerFactory.getLogger(MementoTransformer.class);
    
    public static <T> T transformIdsToLocations(RebindContext rebindContext, Object value, Class<T> requiredType, boolean removeDanglingRefs) {
        if (value == null) {
            return (T) value;
            
        } else if (Location.class.isAssignableFrom(requiredType)) {
            Location loc = rebindContext.getLocation((String)value);
            if (loc == null) {
                if (removeDanglingRefs) {
                    LOG.warn("No location found for "+value+"; returning null for "+requiredType.getSimpleName());
                } else {
                    throw new IllegalStateException("No location found for "+value);
                }
            }
            return (T) loc;
            
        } else if (Iterable.class.isAssignableFrom(requiredType)) {
            Collection<Location> result = Lists.newArrayList();
            for (String id : (Iterable<String>)value) {
                Location loc = rebindContext.getLocation(id);
                if (loc == null) {
                    if (removeDanglingRefs) {
                        LOG.warn("No location found for "+id+"; discarding reference from "+requiredType.getSimpleName());
                    } else {
                        throw new IllegalStateException("No location found for "+id);
                    }
                } else {
                    result.add(loc);
                }
            }
            
            if (Set.class.isAssignableFrom(requiredType)) {
                result = Sets.newLinkedHashSet(result);
            }
            if (!requiredType.isAssignableFrom(result.getClass())) {
                LOG.warn("Cannot transform ids to locations of type "+requiredType+"; returning "+result.getClass());
            }
            return (T) result;
            
        } else if (value instanceof Map) {
            LinkedHashMap<Object,Location> result = Maps.newLinkedHashMap();
            for (Map.Entry<?, String> entry : ((Map<?,String>)value).entrySet()) {
                String id = entry.getValue();
                Location loc = rebindContext.getLocation(id);
                if (loc == null) {
                    if (removeDanglingRefs) {
                        LOG.warn("No location found for "+id+"; discarding reference from "+requiredType.getSimpleName());
                    } else {
                        throw new IllegalStateException("No location found for "+id);
                    }
                } else {
                    result.put(entry.getKey(), loc);
                }
            }
            
            if (!requiredType.isAssignableFrom(LinkedHashMap.class)) {
                LOG.warn("Cannot transform ids to locations of type "+requiredType+"; returning LinkedHashMap!");
            }
            return (T) result;
            
        } else {
            throw new IllegalStateException("Cannot transform ids to locations of type "+requiredType);
        }
    }

    public static <T> T transformIdsToEntities(RebindContext rebindContext, Object value, Class<T> requiredType, boolean removeDanglingRefs) {
        return transformIdsToEntities(rebindContext, value, TypeToken.of(requiredType), removeDanglingRefs);
    }
    
    public static <T> T transformIdsToEntities(RebindContext rebindContext, Object value, TypeToken<T> requiredType, boolean removeDanglingRefs) {
        Class<? super T> requiredRawType = requiredType.getRawType();
        
        if (value == null) {
            return null;
            
        } else if (Entity.class.isAssignableFrom(requiredRawType)) {
            Entity entity = rebindContext.getEntity((String)value);
            if (entity == null) {
                if (removeDanglingRefs) {
                    LOG.warn("No entity found for "+value+"; return null for "+requiredRawType.getSimpleName());
                } else {
                    throw new IllegalStateException("No entity found for "+value);
                }
            }
            return (T) entity;
            
        } else if (Iterable.class.isAssignableFrom(requiredRawType)) {
            Collection<Entity> result = Lists.newArrayList();
            for (String id : (Iterable<String>)value) {
                Entity entity = rebindContext.getEntity(id);
                if (entity == null) {
                    if (removeDanglingRefs) {
                        LOG.warn("No entity found for "+id+"; discarding reference from "+requiredRawType.getSimpleName());
                    } else {
                        throw new IllegalStateException("No entity found for "+id);
                    }
                } else {
                    result.add(entity);
                }
            }

            if (Set.class.isAssignableFrom(requiredRawType)) {
                result = Sets.newLinkedHashSet(result);
            }
            if (!requiredType.isAssignableFrom(result.getClass())) {
                LOG.warn("Cannot transform ids to entities of type "+requiredType+"; returning "+result.getClass());
            }
            return (T) result;
                
        } else if (value instanceof Map) {
            // If told explicitly the generics, then use that; but otherwise default to the value being of type Entity
            TypeToken<?> keyType = requiredType.resolveType(Map.class.getTypeParameters()[0]);
            TypeToken<?> valueType = requiredType.resolveType(Map.class.getTypeParameters()[1]);
            boolean keyIsEntity = Entity.class.isAssignableFrom(keyType.getRawType());
            boolean valueIsEntity = Entity.class.isAssignableFrom(valueType.getRawType()) || !keyIsEntity;
            
            Map<Object,Object> result = Maps.newLinkedHashMap();
            for (Map.Entry<?, ?> entry : ((Map<?,?>)value).entrySet()) {
                Object key = entry.getKey();
                Object val = entry.getValue();
                
                if (keyIsEntity) {
                    key = rebindContext.getEntity((String)key);
                    if (key == null) {
                        if (removeDanglingRefs) {
                            LOG.warn("No entity found for "+entry.getKey()+"; discarding reference from "+requiredRawType.getSimpleName());
                            continue;
                        } else {
                            throw new IllegalStateException("No entity found for "+entry.getKey());
                        }
                    }
                }
                if (valueIsEntity) {
                    val = rebindContext.getEntity((String)val);
                    if (val == null) {
                        if (removeDanglingRefs) {
                            LOG.warn("No entity found for "+entry.getValue()+"; discarding reference from "+requiredRawType.getSimpleName());
                            continue;
                        } else {
                            throw new IllegalStateException("No entity found for "+entry.getValue());
                        }
                    }
                }
                
                result.put(key, val);
            }
            
            if (!requiredType.isAssignableFrom(LinkedHashMap.class)) {
                LOG.warn("Cannot transform ids to entities of type "+requiredType+"; returning LinkedHashMap!");
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
    	if (containsType(vs, Location.class)) {
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
    	if (containsType(vs.values(), Location.class)) {
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
        if (containsType(vs, Entity.class)) {
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
        if (containsType(vs.values(), Entity.class) || containsType(vs.keySet(), Entity.class)) {
            // requires transforming for serialization
            Map<Object,Object> result = Maps.newLinkedHashMap();
            for (Map.Entry<?,?> entry : vs.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                Object k2 = (k instanceof Entity) ? ((Entity)k).getId() : k;
                Object v2 = (v instanceof Entity) ? ((Entity)v).getId() : v;
                result.put(k2, v2);
            }
            return result;
        } else {
            return vs;
        }
    }

    /**
     * Returns true if the first non-null element is of the given type. Otherwise returns false 
     * (including if empty or all nulls). This is a sufficient check, because we're assuming that 
     * if it contains a location/element then everything in there is a location/element.
     */
    private static <T> boolean containsType(Iterable<T> vals, Class<?> clazz) {
    	for (T val : vals) {
    	    if (val != null) {
    	        return clazz.isInstance(val);
    	    }
    	}
    	return false;
    }
}
