package io.brooklyn.camp.brooklyn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogItemType;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate.Builder;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.Service;
import io.brooklyn.camp.spi.resolve.PdpMatcher;

public class BrooklynEntityMatcher implements PdpMatcher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynEntityMatcher.class);
    
    protected final ManagementContext mgmt;

    public BrooklynEntityMatcher(ManagementContext bmc) {
        this.mgmt = bmc;
    }

    @Override
    public boolean accepts(Object deploymentPlanItem) {
        return lookup(deploymentPlanItem) != null;
    }

    /** returns a CatalogItem (if id matches), Class<Entity> (if type matches), or null */
    protected Object lookup(Object deploymentPlanItem) {
        if (deploymentPlanItem instanceof Service) {
            String serviceType = ((Service)deploymentPlanItem).getServiceType();
            CatalogItem<?> catalogItem = mgmt.getCatalog().getCatalogItem( serviceType );
            if (catalogItem!=null && catalogItem.getCatalogItemType()==CatalogItemType.ENTITY) 
                return catalogItem;
            
            // TODO should put everything in catalog, rather than attempt loading classes!
            
            try {
                Class<? extends Entity> type = mgmt.getCatalog().loadClassByType(serviceType, Entity.class);
                if (type!=null) 
                    return type;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.debug("Item "+serviceType+" not known in catalog: "+e);
            }

            try {
                Class<?> type = mgmt.getCatalog().getRootClassLoader().loadClass(serviceType);
                if (type!=null) 
                    return type;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.debug("Item "+serviceType+" not known in classpath: "+e);
            }
        }
        return null;
    }

    @Override
    public boolean apply(Object deploymentPlanItem, AssemblyTemplateConstructor atc) {
        Object item = lookup(deploymentPlanItem);
        if (item==null) return false;

        log.debug("Item "+deploymentPlanItem+" being instantiated with "+item);

        Builder<? extends PlatformComponentTemplate> builder = PlatformComponentTemplate.builder();
        
        if (item instanceof CatalogItem) {
            builder.id( ((CatalogItem<?>)item).getId() );
        } else if (item instanceof Class) {
            builder.id( ((Class<?>) item).getCanonicalName() );
        } else {
            throw new IllegalStateException("Item "+item+" is not recognised here");
        }
        
        // TODO other config items
        
        atc.add(builder.build());
        
        return true;
    }

}
