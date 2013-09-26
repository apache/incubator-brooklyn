package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.AggregatingCampPlatform;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;
import io.brooklyn.camp.brooklyn.spi.lookup.BrooklynUrlLookup;
import io.brooklyn.camp.brooklyn.spi.platform.BrooklynImmutableCampPlatform;
import io.brooklyn.camp.brooklyn.spi.platform.HasBrooklynManagementContext;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.config.BrooklynProperties;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;

/** {@link CampPlatform} implementation which includes Brooklyn entities 
 * (via {@link BrooklynImmutableCampPlatform})
 * and allows customisation / additions */
public class BrooklynCampPlatform extends AggregatingCampPlatform implements HasBrooklynManagementContext {

    private final ManagementContext bmc;

    public BrooklynCampPlatform(PlatformRootSummary root, ManagementContext managementContext) {
        super(root);
        addPlatform(new BrooklynImmutableCampPlatform(root, managementContext));
        
        this.bmc = managementContext;
        
        addMatchers();
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    protected void addMatchers() {
        // TODO artifacts
        pdp().addMatcher(new BrooklynEntityMatcher(bmc));
    }

    // --- runtime
    
    public static void main(String[] args) {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .start();
        
        ((BrooklynProperties)launcher.getServerDetails().getManagementContext().getConfig()).
            put(BrooklynUrlLookup.BROOKLYN_ROOT_URL, launcher.getServerDetails().getWebServerUrl());
        
        BrooklynCampPlatform p = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                launcher.getServerDetails().getManagementContext());
        new CampServer(p, "").start();
    }

}
