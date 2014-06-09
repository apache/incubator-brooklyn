package brooklyn.management.entitlement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** 
 * Entitlement lookup relies on:
 * <li>an "entitlement context", consisting of at minimum a string identifier of the user/actor for which entitlement is being requested
 * <li>an "entitlement class", representing the category of activity for which entitlement is being requested
 * <li>an "entitlement class argument", representing the specifics of the activity for which entitlement is being requested 
 */
public interface EntitlementManager {

    public <T> boolean isEntitled(@Nullable EntitlementContext context, @Nonnull EntitlementClass<T> entitlementClass, @Nullable T entitlementClassArgument);
    
}
