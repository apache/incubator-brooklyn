package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.AbstractResourceLookup;
import io.brooklyn.camp.spi.collection.BasicResourceLookup;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;


public class BrooklynCampPlatform extends CampPlatform {

    private final ManagementContext bmc;
    private PlatformComponentTemplateBrooklynLookup pct;
    private BasicResourceLookup<ApplicationComponentTemplate> act;

    public BrooklynCampPlatform(PlatformRootSummary root, ManagementContext managementContext) {
        super(root);
        this.bmc = managementContext;
        
        // PCT's come from brooklyn
        pct = new PlatformComponentTemplateBrooklynLookup(root(), getBrooklynManagementContext());
        
        // ACT's are not known in brooklyn (everything comes in as config) -- to be extended to support!
        act = new BasicResourceLookup<ApplicationComponentTemplate>();
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    // --- camp comatibility setup
    
    @Override
    public AbstractResourceLookup<PlatformComponentTemplate> platformComponentTemplates() {
        return pct;
    }

    @Override
    public AbstractResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates() {
        return act;
    }

    // --- runtime
    
    public static void main(String[] args) {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
//                .webconsolePort(port)
//                .location(location)
                .start();
        
        BrooklynCampPlatform p = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                launcher.getServerDetails().getManagementContext());
        new CampServer(p, "").start();
    }

}
