package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;

import com.google.common.collect.Maps;

/**
 * Creates locations of required types.
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * @author aled
 */
public class InternalLocationFactory {

    private final ManagementContextInternal managementContext;

    /**
     * For tracking if AbstractLocation constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing locations directly (and expecting configure() to be
     * called inside the constructor, etc).
     * 
     * @author aled
     */
    public static class FactoryConstructionTracker {
        private static ThreadLocal<Boolean> constructing = new ThreadLocal<Boolean>();
        
        public static boolean isConstructing() {
            return (constructing.get() == Boolean.TRUE);
        }
        
        static void reset() {
            constructing.set(Boolean.FALSE);
        }
        
        static void setConstructing() {
            constructing.set(Boolean.TRUE);
        }
    }

    /**
     * Returns true if this is a "new-style" location (i.e. where not expected to call the constructor to instantiate it).
     * 
     * @param managementContext
     * @param clazz
     */
    public static boolean isNewStyleLocation(ManagementContext managementContext, Class<?> clazz) {
        try {
            return isNewStyleLocation(clazz);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static boolean isNewStyleLocation(Class<?> clazz) {
        if (!Location.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an location");
        }
        
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public InternalLocationFactory(ManagementContextInternal managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Location> T createLocation(LocationSpec<T> spec) {
        if (spec.getFlags().containsKey("parent")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent; use spec.parent() instead for "+spec);
        }
        
        try {
            Class<? extends T> clazz = spec.getType();
            
            FactoryConstructionTracker.setConstructing();
            T loc;
            try {
                loc = construct(clazz, spec);
            } finally {
                FactoryConstructionTracker.reset();
            }
            
            if (spec.getDisplayName()!=null)
                ((AbstractLocation)loc).setName(spec.getDisplayName());
            
            if (isNewStyleLocation(clazz)) {
                ((AbstractLocation)loc).setManagementContext(managementContext);
                ((AbstractLocation)loc).configure(ConfigBag.newInstance().putAll(spec.getFlags()).putAll(spec.getConfig()).getAllConfig());
            }
            
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((AbstractLocation)loc).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }
            ((AbstractLocation)loc).init();
            
            Location parent = spec.getParent();
            if (parent != null) {
                loc.setParent(parent);
            }
            return loc;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private <T extends Location> T construct(Class<? extends T> clazz, LocationSpec<T> spec) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (isNewStyleLocation(clazz)) {
            return clazz.newInstance();
        } else {
            return constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
        }
    }
    
    private <T extends Location> T constructOldStyle(Class<? extends T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (flags.containsKey("parentLocation")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+clazz);
        }
        Constructor<? extends T> c2 = Reflections.findCallabaleConstructor(clazz, new Object[] {flags});
        if (c2 != null) {
            return c2.newInstance(Maps.newLinkedHashMap(flags));
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
}
