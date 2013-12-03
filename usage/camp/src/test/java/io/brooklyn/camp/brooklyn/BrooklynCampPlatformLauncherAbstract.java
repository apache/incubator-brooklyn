package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.annotations.Beta;

/** launcher for {@link BrooklynCampPlatform}, which may or may not start a (web) server depending on children */
@Beta
public abstract class BrooklynCampPlatformLauncherAbstract {

    protected BrooklynCampPlatform platform;
    protected LocalManagementContext mgmt;
    
    public void launch() {
        assert platform == null;

        mgmt = newMgmtContext();        
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                mgmt);
    }

    protected LocalManagementContext newMgmtContext() {
        return new LocalManagementContext();
    }

    public ManagementContext getBrooklynMgmt() {
        return mgmt;
    }
    
    public BrooklynCampPlatform getCampPlatform() {
        return platform;
    }

}
