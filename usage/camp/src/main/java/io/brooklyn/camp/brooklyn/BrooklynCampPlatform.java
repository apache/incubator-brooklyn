package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.impl.PlatformComponentTemplate;
import io.brooklyn.camp.impl.PlatformRootSummary;
import io.brooklyn.camp.util.collection.AbstractResourceListProvider;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;


public class BrooklynCampPlatform extends CampPlatform {

    private final ManagementContext bmc;
    PlatformComponentTemplateBrooklynLookup pct;

    public BrooklynCampPlatform(ManagementContext managementContext) {
        this.bmc = managementContext;
        pct = new PlatformComponentTemplateBrooklynLookup(this);
    }

    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    public static void main(String[] args) {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
//                .webconsolePort(port)
//                .location(location)
                .start();
        
        BrooklynCampPlatform p = new BrooklynCampPlatform(launcher.getServerDetails().getManagementContext());
        new CampServer(p, "").start();
    }

    // ---
    
    @Override
    protected PlatformRootSummary initializeRoot() {
        return PlatformRootSummary.builder().
                name("Brooklyn CAMP Platform").
                build();
    }
    
    @Override
    public AbstractResourceListProvider<PlatformComponentTemplate> platformComponentTemplates() {
        return pct;
    }
    
}
