package brooklyn.camp.lite;

import io.brooklyn.camp.BasicCampPlatform;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;

public class CampPlatformWithJustBrooklynMgmt extends BasicCampPlatform implements HasBrooklynManagementContext {

    private ManagementContext mgmt;

    public CampPlatformWithJustBrooklynMgmt(ManagementContext mgmt) {
        this.mgmt = mgmt;
        ((BrooklynProperties)mgmt.getConfig()).put(BrooklynServerConfig.CAMP_PLATFORM, this);
    }
    
    @Override
    public ManagementContext getBrooklynManagementContext() {
        return mgmt;
    }

}
