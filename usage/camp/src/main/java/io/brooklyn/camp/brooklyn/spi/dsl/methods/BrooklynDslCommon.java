package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import io.brooklyn.camp.brooklyn.spi.dsl.DslUtils;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

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

    /** returns any public static fields such as config keys or attribute sensors */
    public static Object getStaticField(String clazzName, String fieldName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            Field field = clazz.getField(fieldName);
            if (!Modifier.isStatic(field.getModifiers()))
                throw new IllegalArgumentException("Cannot get static field: field " + fieldName + " in class " + clazzName + " is not static");
            return field.get(null);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}
