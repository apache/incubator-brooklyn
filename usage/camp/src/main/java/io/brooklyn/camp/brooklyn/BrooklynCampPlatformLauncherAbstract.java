package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.annotations.Beta;

/** launcher for {@link BrooklynCampPlatform}, which may or may not start a (web) server depending on children */
@Beta
public abstract class BrooklynCampPlatformLauncherAbstract {

    protected BrooklynCampPlatform platform;
    protected ManagementContext mgmt;
    
    public BrooklynCampPlatformLauncherAbstract useManagementContext(ManagementContext mgmt) {
        if (this.mgmt!=null && mgmt!=this.mgmt)
            throw new IllegalStateException("Attempt to change mgmt context; not supported.");
        
        this.mgmt = mgmt;
        
        return this;
    }
    
    public BrooklynCampPlatformLauncherAbstract launch() {
        if (platform!=null)
            throw new IllegalStateException("platform already created");

        if (getBrooklynMgmt()==null)
            useManagementContext(newMgmtContext());
        
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                getBrooklynMgmt())
            .setConfigKeyAtManagmentContext();
        
        return this;
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

    /** stops any servers (camp and brooklyn) launched by this launcher */
    public abstract void stopServers() throws Exception;

}
