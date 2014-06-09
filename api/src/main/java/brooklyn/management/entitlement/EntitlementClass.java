package brooklyn.management.entitlement;

import com.google.common.reflect.TypeToken;

/** @see EntitlementManager */
public interface EntitlementClass<T> {
    String entitlementClassIdentifier();
    TypeToken<T> aentitlementClassArgumentType();
}
