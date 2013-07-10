package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.util.ArrayList;
import java.util.List;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Application;
import brooklyn.management.ManagementContext;

public class AssemblyTemplateBrooklynLookup extends AbstractTemplateBrooklynLookup<AssemblyTemplate> {

    public AssemblyTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public AssemblyTemplate adapt(CatalogItem<?> item) {
        return AssemblyTemplate.builder().
                name(item.getName()).
                id(item.getId()).
                description(item.getDescription()).
                created(root.getCreated()).
                instantiator(BrooklynAssemblyTemplateInstantiator.class).
                build();
    }

    @Override
    public List<ResolvableLink<AssemblyTemplate>> links() {
        Iterable<CatalogItem<Application>> l = bmc.getCatalog().getCatalogItems(CatalogPredicates.IS_TEMPLATE);
        List<ResolvableLink<AssemblyTemplate>> result = new ArrayList<ResolvableLink<AssemblyTemplate>>();
        for (CatalogItem<Application> li: l)
            result.add(newLink(li));
        return result;
    }
    
}
