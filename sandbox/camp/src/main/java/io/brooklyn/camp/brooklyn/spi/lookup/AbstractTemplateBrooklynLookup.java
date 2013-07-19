package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;

public abstract class AbstractTemplateBrooklynLookup<T extends AbstractResource>  extends AbstractBrooklynResourceLookup<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractTemplateBrooklynLookup.class);
    
    public AbstractTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public T get(String id) {
        CatalogItem<?> item = bmc.getCatalog().getCatalogItem(id);
        if (item==null) {
            log.warn("Could not find item '"+id+"' in Brooklyn catalog; returning null");
            return null;
        }
        return adapt(item);
    }

    public abstract T adapt(CatalogItem<?> item);

    protected ResolvableLink<T> newLink(CatalogItem<? extends Entity> li) {
        return newLink(li.getId(), li.getName());
    }

}
