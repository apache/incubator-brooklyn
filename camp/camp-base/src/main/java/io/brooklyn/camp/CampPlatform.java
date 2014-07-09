package io.brooklyn.camp;

import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.collection.ResourceLookup;
import io.brooklyn.camp.spi.resolve.PdpProcessor;

import com.google.common.base.Preconditions;

public abstract class CampPlatform {

    private final PlatformRootSummary root;
    private final PdpProcessor pdp;

    public CampPlatform(PlatformRootSummary root) {
        this.root = Preconditions.checkNotNull(root, "root");
        pdp = createPdpProcessor();
    }

    // --- root
    
    public PlatformRootSummary root() {
        return root;
    }

    // --- other aspects
    
    public PdpProcessor pdp() {
        return pdp;
    }

    
    // --- required custom implementation hooks
    
    public abstract ResourceLookup<PlatformComponentTemplate> platformComponentTemplates();
    public abstract ResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates();
    public abstract ResourceLookup<AssemblyTemplate> assemblyTemplates();

    public abstract ResourceLookup<PlatformComponent> platformComponents();
    public abstract ResourceLookup<ApplicationComponent> applicationComponents();
    public abstract ResourceLookup<Assembly> assemblies();

    /** returns object where changes to a PDP can be made; note all changes must be committed */
    public abstract PlatformTransaction transaction();

    // --- optional customisation overrides
    
    protected PdpProcessor createPdpProcessor() {
        return new PdpProcessor(this);
    }

}
