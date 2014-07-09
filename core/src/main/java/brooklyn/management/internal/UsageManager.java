package brooklyn.management.internal;

import java.util.Set;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.Location;
import brooklyn.management.usage.ApplicationUsage;
import brooklyn.management.usage.LocationUsage;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;

@Beta
public interface UsageManager {

    /**
     * Adds this application event to the usage record for the given app (creating the usage 
     * record if one does not already exist).
     */
    void recordApplicationEvent(Application app, Lifecycle state);
    
    /**
     * Adds this location event to the usage record for the given location (creating the usage 
     * record if one does not already exist).
     */
    void recordLocationEvent(Location loc, Lifecycle state);

    /**
     * Returns the usage info for the location with the given id, or null if unknown.
     */
    LocationUsage getLocationUsage(String locationId);
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find locations used within a given time period.
     */
    Set<LocationUsage> getLocationUsage(Predicate<? super LocationUsage> filter);
    
    /**
     * Returns the usage info for the application with the given id, or null if unknown.
     */
    ApplicationUsage getApplicationUsage(String appId);
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find applications used within a given time period.
     */
    Set<ApplicationUsage> getApplicationUsage(Predicate<? super ApplicationUsage> filter);

}
