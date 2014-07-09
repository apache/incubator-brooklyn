package brooklyn.management.entitlement;

import java.util.Map;

import com.google.common.base.Preconditions;

import brooklyn.util.collections.MutableMap;

public class PerUserEntitlementManagerWithDefault implements EntitlementManager {

    protected final EntitlementManager defaultManager;
    protected final Map<String,EntitlementManager> perUserManagers = MutableMap.of();

    public PerUserEntitlementManagerWithDefault(EntitlementManager defaultManager) {
        this.defaultManager = Preconditions.checkNotNull(defaultManager);
    }

    public void addUser(String user, EntitlementManager managerForThisUser) {
        perUserManagers.put(user, managerForThisUser);
    }

    @Override
    public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
        EntitlementManager entitlementInEffect = null;
        if (context!=null) entitlementInEffect = perUserManagers.get(context.user());
        if (entitlementInEffect==null) entitlementInEffect = defaultManager; 
        return entitlementInEffect.isEntitled(context, entitlementClass, entitlementClassArgument);
    }
    
}
