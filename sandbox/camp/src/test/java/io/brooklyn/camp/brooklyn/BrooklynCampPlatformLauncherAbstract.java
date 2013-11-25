package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.brooklyn.spi.lookup.BrooklynUrlLookup;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.config.BrooklynProperties;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;

import com.google.common.annotations.Beta;

/** launcher for {@link BrooklynCampPlatform}, which may or may not start a (web) server depending on children */
@Beta
public abstract class BrooklynCampPlatformLauncherAbstract {

    protected BrooklynLauncher launcher;
    protected BrooklynCampPlatform platform;
    
    public void launch() {
        assert launcher == null;
        assert platform == null;
        
        launcher = BrooklynLauncher.newInstance().start();
        ((BrooklynProperties)launcher.getServerDetails().getManagementContext().getConfig()).
            put(BrooklynUrlLookup.BROOKLYN_ROOT_URL, launcher.getServerDetails().getWebServerUrl());
        
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                launcher.getServerDetails().getManagementContext());
        
        launchServers();
    }

    public abstract void launchServers();
    
    public BrooklynLauncher getBrooklynLauncher() {
        return launcher;
    }

    public ManagementContext getBrooklynMgmt() {
        return launcher.getServerDetails().getManagementContext();
    }
    
    public BrooklynCampPlatform getCampPlatform() {
        return platform;
    }

}
