package brooklyn.management.internal;

import java.util.Set;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.Location;
import brooklyn.management.usage.ApplicationUsage;
import brooklyn.management.usage.LocationUsage;

import com.google.common.base.Predicate;


public class NonDeploymentUsageManager implements UsageManager {

    private final ManagementContextInternal initialManagementContext;
    
    public NonDeploymentUsageManager(ManagementContextInternal initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }

    @Override
    public void recordApplicationEvent(Application app, Lifecycle state) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().recordApplicationEvent(app, state);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void recordLocationEvent(Location loc, Lifecycle state) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().recordLocationEvent(loc, state);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public LocationUsage getLocationUsage(String locationId) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getLocationUsage(locationId);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public Set<LocationUsage> getLocationUsage(Predicate<? super LocationUsage> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getLocationUsage(filter);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public ApplicationUsage getApplicationUsage(String appId) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getApplicationUsage(appId);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public Set<ApplicationUsage> getApplicationUsage(Predicate<? super ApplicationUsage> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getApplicationUsage(filter);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }
}
