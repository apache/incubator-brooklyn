package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;


public class AssemblyBrooklynLookup extends AbstractBrooklynResourceLookup<Assembly> {

    private PlatformComponentBrooklynLookup pcs;

    public AssemblyBrooklynLookup(PlatformRootSummary root, ManagementContext bmc, PlatformComponentBrooklynLookup pcs) {
        super(root, bmc);
        this.pcs = pcs;
    }

    @Override
    public Assembly get(String id) {
        Entity entity = bmc.getEntityManager().getEntity(id);
        if (!(entity instanceof Application))
            throw new IllegalArgumentException("Element for "+id+" is not an Application ("+entity+")");
        Assembly.Builder<? extends Assembly> builder = Assembly.builder()
                .created(new Date(entity.getCreationTime()))
                .id(entity.getId())
                .name(entity.getDisplayName());
        
        builder.customAttribute("externalManagementUri", BrooklynUrlLookup.getUrl(bmc, entity));
        
        for (Entity child: entity.getChildren())
            // FIXME this walks the whole damn tree!
            builder.add( pcs.get(child.getId() ));
        return builder.build();
    }

    @Override
    public List<ResolvableLink<Assembly>> links() {
        List<ResolvableLink<Assembly>> result = new ArrayList<ResolvableLink<Assembly>>();
        for (Application app: bmc.getApplications())
            result.add(newLink(app.getId(), app.getDisplayName()));
        return result;
    }

}
