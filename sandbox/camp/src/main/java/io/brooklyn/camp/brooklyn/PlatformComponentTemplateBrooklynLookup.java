package io.brooklyn.camp.brooklyn;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;

import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.AbstractResourceLookup;
import io.brooklyn.camp.spi.collection.ResolvableLink;

public class PlatformComponentTemplateBrooklynLookup extends AbstractResourceLookup<PlatformComponentTemplate> {

    private static final Logger log = LoggerFactory.getLogger(PlatformComponentTemplateBrooklynLookup.class);
    
    private final PlatformRootSummary root;
    private final ManagementContext bmc;

    public PlatformComponentTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        this.root = root;
        this.bmc = bmc;
    }

    @Override
    public PlatformComponentTemplate get(String id) {
        CatalogItem<?> item = bmc.getCatalog().getCatalogItem(id);
        if (item==null) {
            log.warn("Could not find item '"+id+"' in Brooklyn catalog; returning null");
            return null;
        }
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

    protected ResolvableLink<PlatformComponentTemplate> newLink(CatalogItem<Entity> li) {
        return newLink(li.getId(), li.getName());
    }

}
