package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.brooklyn.spi.lookup.AssemblyBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.AssemblyTemplateBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.BrooklynUrlLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.PlatformComponentBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.PlatformComponentTemplateBrooklynLookup;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.collection.BasicResourceLookup;
import io.brooklyn.camp.spi.collection.ResourceLookup;
import io.brooklyn.camp.spi.collection.ResourceLookup.EmptyResourceLookup;
import brooklyn.config.BrooklynProperties;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;


public class BrooklynCampPlatform extends CampPlatform {

    private final ManagementContext bmc;
    private final AssemblyTemplateBrooklynLookup ats;
    private final PlatformComponentTemplateBrooklynLookup pcts;
    private final BasicResourceLookup<ApplicationComponentTemplate> acts;
    private final PlatformComponentBrooklynLookup pcs;
    private final AssemblyBrooklynLookup assemblies;

    public BrooklynCampPlatform(PlatformRootSummary root, ManagementContext managementContext) {
        super(root);
        this.bmc = managementContext;
        
        // these come from brooklyn
        pcts = new PlatformComponentTemplateBrooklynLookup(root(), getBrooklynManagementContext());
        ats = new AssemblyTemplateBrooklynLookup(root(), getBrooklynManagementContext());
        pcs = new PlatformComponentBrooklynLookup(root(), getBrooklynManagementContext());
        assemblies = new AssemblyBrooklynLookup(root(), getBrooklynManagementContext(), pcs);
        
        // ACT's are not known in brooklyn (everything comes in as config) -- to be extended to support!
        acts = new BasicResourceLookup<ApplicationComponentTemplate>();
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    // --- camp comatibility setup
    
    @Override
    public ResourceLookup<PlatformComponentTemplate> platformComponentTemplates() {
        return pcts;
    }

    @Override
    public ResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates() {
        return acts;
    }

    @Override
    public ResourceLookup<AssemblyTemplate> assemblyTemplates() {
        return ats;
    }
    
    @Override
    public ResourceLookup<PlatformComponent> platformComponents() {
        return pcs;
    }

    @Override
    public ResourceLookup<ApplicationComponent> applicationComponents() {
        return new EmptyResourceLookup<ApplicationComponent>();
    }

    @Override
    public ResourceLookup<Assembly> assemblies() {
        return assemblies;
    }
    
    @Override
    public PlatformTransaction transaction() {
        throw new IllegalStateException("Brooklyn does not support adding new items");
    }
    
    // --- runtime
    
    public static void main(String[] args) {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
//                .webconsolePort(port)
//                .location(location)
                .start();
        
        ((BrooklynProperties)launcher.getServerDetails().getManagementContext().getConfig()).
            put(BrooklynUrlLookup.BROOKLYN_ROOT_URL, launcher.getServerDetails().getWebServerUrl());
        
        BrooklynCampPlatform p = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                launcher.getServerDetails().getManagementContext());
        new CampServer(p, "").start();
    }

}
