package brooklyn.management.internal;

import brooklyn.management.ManagementContext;

public class ManagementTransitionInfo {

    final ManagementContext mgmtContext;
    
    final ManagementTransitionMode mode;
    
    /** true if this transition is an entity whose mastering is migrating from one node to another;
     * false if the brooklyn mgmt plane is just starting managing of this entity for the very first time  
     */

    public enum ManagementTransitionMode {
        /** Entity is being created fresh, for the first time, or stopping permanently */ 
        NORMAL,
        /** Entity management is moving from one location to another (ie stopping at one location / starting at another) */
        MIGRATORY, 
        /** Entity is being created, from a serialized/specified state */
        REBIND
    }
    
    public ManagementTransitionInfo(ManagementContext mgmtContext, ManagementTransitionMode mode) {
        this.mgmtContext = mgmtContext;
        this.mode = mode;
    }
    
    
    public ManagementContext getManagementContext() {
        return mgmtContext;
    }

    public ManagementTransitionMode getMode() {
        return mode;
    }
}
