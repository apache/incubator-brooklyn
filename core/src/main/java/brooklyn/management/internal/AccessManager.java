package brooklyn.management.internal;

import brooklyn.management.AccessController;

import com.google.common.annotations.Beta;

@Beta
public interface AccessManager {

    AccessController getAccessController();
    
    boolean isLocationProvisioningAllowed();

    boolean isLocationManagementAllowed();

    boolean isEntityManagementAllowed();

    void setLocationProvisioningAllowed(boolean allowed);
    
    void setLocationManagementAllowed(boolean allowed);
    
    void setEntityManagementAllowed(boolean allowed);
}
