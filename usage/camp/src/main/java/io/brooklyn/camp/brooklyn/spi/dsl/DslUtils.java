package io.brooklyn.camp.brooklyn.spi.dsl;

import brooklyn.util.task.DeferredSupplier;

public class DslUtils {

    /** true iff none of the args are deferred / tasks */
    public static boolean resolved(final Object... args) {
        boolean allResolved = true;
        for (Object arg: args) {
            if (arg instanceof DeferredSupplier<?>) {
                allResolved = false;
                break;
            }
        }
        return allResolved;
    }

}
