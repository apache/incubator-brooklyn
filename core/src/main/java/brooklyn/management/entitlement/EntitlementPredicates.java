package brooklyn.management.entitlement;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;

public class EntitlementPredicates {

    public static <T> Predicate<T> hasEntitlementClass(final EntitlementManager entitlementManager, final EntitlementClass<T> entitlementClass) {
        return new Predicate<T>() {
            @Override
            public boolean apply(@Nullable T t) {
                return Entitlements.isEntitled(entitlementManager, entitlementClass, t);
            }
        };
    }

}
