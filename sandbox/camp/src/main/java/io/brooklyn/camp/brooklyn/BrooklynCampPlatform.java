package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.impl.ApplicationComponentTemplate;
import io.brooklyn.camp.impl.PlatformComponentTemplate;
import io.brooklyn.camp.impl.PlatformRootSummary;
import io.brooklyn.camp.util.collection.AbstractResourceListProvider;
import io.brooklyn.camp.util.collection.BasicResourceListProvider;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;


public class BrooklynCampPlatform extends CampPlatform {

    private final ManagementContext bmc;
    private PlatformComponentTemplateBrooklynLookup pct;
    private BasicResourceListProvider<ApplicationComponentTemplate> act;

    public BrooklynCampPlatform(ManagementContext managementContext) {
        this.bmc = managementContext;
        
        // PCT's come from brooklyn
        pct = new PlatformComponentTemplateBrooklynLookup(this);
        
        // ACT's are not known in brooklyn (everything comes in as config) -- to be extended to support!
        act = new BasicResourceListProvider<ApplicationComponentTemplate>();
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    // --- camp comatibility setup
    
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

    @Override
    public AbstractResourceListProvider<ApplicationComponentTemplate> applicationComponentTemplates() {
        return act;
    }

    // --- runtime
    
    public static void main(String[] args) {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
//                .webconsolePort(port)
//                .location(location)
                .start();
        
        BrooklynCampPlatform p = new BrooklynCampPlatform(launcher.getServerDetails().getManagementContext());
        new CampServer(p, "").start();
    }

}
