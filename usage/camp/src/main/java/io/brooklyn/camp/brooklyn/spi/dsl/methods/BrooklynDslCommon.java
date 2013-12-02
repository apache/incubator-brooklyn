package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import io.brooklyn.camp.brooklyn.spi.dsl.DslUtils;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.Task;

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

}
