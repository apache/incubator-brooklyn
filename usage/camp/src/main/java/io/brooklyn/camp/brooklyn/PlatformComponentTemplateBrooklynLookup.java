package io.brooklyn.camp.brooklyn;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;

import io.brooklyn.camp.impl.PlatformComponentTemplate;
import io.brooklyn.camp.util.collection.AbstractResourceListProvider;
import io.brooklyn.camp.util.collection.ResolveableLink;

public class PlatformComponentTemplateBrooklynLookup extends AbstractResourceListProvider<PlatformComponentTemplate> {

    private static final Logger log = LoggerFactory.getLogger(PlatformComponentTemplateBrooklynLookup.class);
    
    private BrooklynCampPlatform platform;

    public PlatformComponentTemplateBrooklynLookup(BrooklynCampPlatform platform) {
        this.platform = platform;
    }

    @Override
    public PlatformComponentTemplate get(String id) {
        CatalogItem<?> item = platform.getBrooklynManagementContext().getCatalog().getCatalogItem(id);
        if (item==null) {
            log.warn("Could not find item '"+id+"' in Brooklyn catalog; returning null");
            return null;
        }
        return PlatformComponentTemplate.builder().
                name(item.getName()).
                id(item.getId()).
                description(item.getDescription()).
                created(platform.root().getCreated()).
                build();
    }

    @Override
    public List<ResolveableLink<PlatformComponentTemplate>> links() {
        Iterable<CatalogItem<Entity>> l = platform.getBrooklynManagementContext().getCatalog().getCatalogItems(CatalogPredicates.IS_ENTITY);
        List<ResolveableLink<PlatformComponentTemplate>> result = new ArrayList<ResolveableLink<PlatformComponentTemplate>>();
        for (CatalogItem<Entity> li: l)
            result.add(new ResolveableLink<PlatformComponentTemplate>(li.getId(), li.getName(), this));
        return result;
    }

}
