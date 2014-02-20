package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import java.util.Map;

import com.google.common.base.Function;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityClassResolver;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import io.brooklyn.camp.brooklyn.spi.dsl.DslUtils;

/** static import functions which can be used in `$brooklyn:xxx` contexts */
public class BrooklynDslCommon {

    public static Object literal(Object expression) {
        return expression;
    }

	public static DslComponent component(String id) {
		return new DslComponent(id);
	}

	/** returns a DslParsedObject<String> OR a String if it is fully resolved */
    public static Object formatString(final String pattern, final Object ...args) {
        if (DslUtils.resolved(args)) {
            // if all args are resolved, apply the format string now
            return String.format(pattern, args);
        }
        return new BrooklynDslDeferredSupplier<String>() {
            @Override
            public Task<String> newTask() {
                return DependentConfiguration.formatString(pattern, args);
            }
        };
    }

    // TODO: Would be nice to have sensor(String sensorName), which would take the sensor from the entity in question, 
    //       but that would require refactoring of Brooklyn DSL
    // TODO: Should use catalog's classloader, rather than Class.forName; how to get that? Should we return a future?!
    /** returns a Sensor from the given entity type */
    @SuppressWarnings("unchecked")
    public static Object sensor(String clazzName, String sensorName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            if (!Entity.class.isAssignableFrom(clazz))
                throw new IllegalArgumentException("Class " + clazzName + " is not an Entity");
            Sensor<?> sensor = new EntityDynamicType((Class<? extends Entity>) clazz).getSensor(sensorName);
            if (sensor == null)
                throw new IllegalArgumentException("Sensor " + sensorName + " not found on class " + clazzName);
            return sensor;
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }

    public static Function<ManagementContext, Class<? extends Entity>> entitySpec(Map<String, Object> arguments) {
        return new BrooklynEntityClassResolver(arguments);
    }
    
}
