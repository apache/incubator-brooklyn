package io.brooklyn.camp;

import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.collection.AggregatingResourceLookup;
import io.brooklyn.camp.spi.collection.ResourceLookup;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

/** A {@link CampPlatform} implementation which is empty but allows adding new items,
 * as well as adding other platforms; meant for subclassing only */
public class AggregatingCampPlatform extends CampPlatform {

    private static final Logger log = LoggerFactory.getLogger(AggregatingCampPlatform.class);
    
    protected AggregatingCampPlatform(PlatformRootSummary root) {
        this(root, new BasicCampPlatform(root));
    }
    
    public AggregatingCampPlatform(PlatformRootSummary root, CampPlatform platformWhereTransactionsOccur) {
        super(root);
        log.debug("Creating {} with main platform: {}", this, platformWhereTransactionsOccur);
        this.mainPlatform = platformWhereTransactionsOccur;
    }
    
    /** platform where additions are made */
    CampPlatform mainPlatform;
    List<CampPlatform> otherPlatformsToSearch = new ArrayList<CampPlatform>();
    
    protected void addPlatform(CampPlatform platform) {
        log.debug("Adding child platform to {}: {}", this, platform);
        otherPlatformsToSearch.add(platform);
    }
    
    protected <T extends AbstractResource> ResourceLookup<T> aggregatingLookup(Function<CampPlatform, ResourceLookup<T>> lookupFunction) {
        List<ResourceLookup<T>> lookups = new ArrayList<ResourceLookup<T>>();
        lookups.add(lookupFunction.apply(mainPlatform));
        for (CampPlatform p: otherPlatformsToSearch)
            lookups.add(lookupFunction.apply(p));
        return AggregatingResourceLookup.of(lookups);
    }
    
    public ResourceLookup<PlatformComponentTemplate> platformComponentTemplates() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<PlatformComponentTemplate>>() {
            public ResourceLookup<PlatformComponentTemplate> apply(@Nullable CampPlatform input) {
                return input.platformComponentTemplates();
            }
        });
    }

    @Override
    public ResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<ApplicationComponentTemplate>>() {
            public ResourceLookup<ApplicationComponentTemplate> apply(@Nullable CampPlatform input) {
                return input.applicationComponentTemplates();
            }
        });
    }

    public ResourceLookup<AssemblyTemplate> assemblyTemplates() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<AssemblyTemplate>>() {
            public ResourceLookup<AssemblyTemplate> apply(@Nullable CampPlatform input) {
                return input.assemblyTemplates();
            }
        });
    }
    
    public ResourceLookup<PlatformComponent> platformComponents() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<PlatformComponent>>() {
            public ResourceLookup<PlatformComponent> apply(@Nullable CampPlatform input) {
                return input.platformComponents();
            }
        });
    }

    @Override
    public ResourceLookup<ApplicationComponent> applicationComponents() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<ApplicationComponent>>() {
            public ResourceLookup<ApplicationComponent> apply(@Nullable CampPlatform input) {
                return input.applicationComponents();
            }
        });
    }

    public ResourceLookup<Assembly> assemblies() {
        return aggregatingLookup(new Function<CampPlatform, ResourceLookup<Assembly>>() {
            public ResourceLookup<Assembly> apply(@Nullable CampPlatform input) {
                return input.assemblies();
            }
        });
    }
    
    @Override
    public PlatformTransaction transaction() {
        return mainPlatform.transaction();
    }
        
}
