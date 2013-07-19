package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponent.Builder;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;


public class PlatformComponentBrooklynLookup extends AbstractBrooklynResourceLookup<PlatformComponent> {

    public PlatformComponentBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public PlatformComponent get(String id) {
        Entity entity = bmc.getEntityManager().getEntity(id);
        Builder<? extends PlatformComponent> builder = PlatformComponent.builder()
            .created(new Date(entity.getCreationTime()))
            .id(entity.getId())
            .name(entity.getDisplayName())
            .externalManagementUri(BrooklynUrlLookup.getUrl(bmc, entity));
        
        for (Entity child: entity.getChildren())
            // FIXME this walks the whole damn tree!
            builder.add( get(child.getId() ));
        return builder.build();
    }

    // platform components are not listed at the top level -- you have to walk the assemblies
    @Override
    public List<ResolvableLink<PlatformComponent>> links() {
        return Collections.emptyList();
    }

}
