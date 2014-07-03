package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.AggregatingCampPlatform;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslInterpreter;
import io.brooklyn.camp.brooklyn.spi.platform.BrooklynImmutableCampPlatform;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.config.BrooklynProperties;
import brooklyn.management.ManagementContext;
import brooklyn.management.ManagementContext.PropertiesReloadListener;

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
        addInterpreters();
        
        managementContext.addPropertiesReloadListener(new PropertiesReloadListener() {
            @Override public void reloaded() {
                setConfigKeyAtManagmentContext();
            }
        });
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    protected void addMatchers() {
        // TODO artifacts
        pdp().addMatcher(new BrooklynEntityMatcher(bmc));
    }
    
    protected void addInterpreters() {
        pdp().addInterpreter(new BrooklynDslInterpreter());
    }

    public BrooklynCampPlatform setConfigKeyAtManagmentContext() {
        ((BrooklynProperties)bmc.getConfig()).put(BrooklynCampConstants.CAMP_PLATFORM, this);
        return this;
    }

}
