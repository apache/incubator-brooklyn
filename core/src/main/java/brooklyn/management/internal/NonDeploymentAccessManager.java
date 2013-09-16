package brooklyn.management.internal;

import brooklyn.management.AccessController;


public class NonDeploymentAccessManager implements AccessManager {

    private final ManagementContextInternal initialManagementContext;
    
    public NonDeploymentAccessManager(ManagementContextInternal initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }

    @Override
    public AccessController getAccessController() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().getAccessController();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isLocationProvisioningAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isLocationProvisioningAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isLocationManagementAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isLocationManagementAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isEntityManagementAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isEntityManagementAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setLocationProvisioningAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setLocationProvisioningAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setLocationManagementAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setLocationManagementAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setEntityManagementAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setEntityManagementAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }
}
