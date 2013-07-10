package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.util.ArrayList;
import java.util.List;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;

public class PlatformComponentTemplateBrooklynLookup extends AbstractTemplateBrooklynLookup<PlatformComponentTemplate> {

    public PlatformComponentTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public PlatformComponentTemplate adapt(CatalogItem<?> item) {
        return PlatformComponentTemplate.builder().
                name(item.getName()).
                id(item.getId()).
                description(item.getDescription()).
                created(root.getCreated()).
                build();
    }

    @Override
    public List<ResolvableLink<PlatformComponentTemplate>> links() {
        Iterable<CatalogItem<Entity>> l = bmc.getCatalog().getCatalogItems(CatalogPredicates.IS_ENTITY);
        List<ResolvableLink<PlatformComponentTemplate>> result = new ArrayList<ResolvableLink<PlatformComponentTemplate>>();
        for (CatalogItem<Entity> li: l)
            result.add(newLink(li));
        return result;
    }

}
