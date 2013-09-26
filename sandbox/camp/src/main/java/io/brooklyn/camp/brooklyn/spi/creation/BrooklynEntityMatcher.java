package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate.Builder;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.Service;
import io.brooklyn.camp.spi.resolve.PdpMatcher;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogItemType;
import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

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
            String rawServiceType = ((Service)deploymentPlanItem).getServiceType();
            String serviceType = Strings.removeFromStart(rawServiceType, "brooklyn:");
            boolean mustBeBrooklyn = !serviceType.equals(rawServiceType);
            
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
            
            if (mustBeBrooklyn)
                throw new IllegalArgumentException("Unknown brooklyn service/entity type: "+rawServiceType);
        }
        return null;
    }

    @Override
    public boolean apply(Object deploymentPlanItem, AssemblyTemplateConstructor atc) {
        Object item = lookup(deploymentPlanItem);
        if (item==null) return false;

        log.debug("Item "+deploymentPlanItem+" being instantiated with "+item);

        Object old = atc.getInstantiator();
        if (old!=null && !old.equals(BrooklynAssemblyTemplateInstantiator.class)) {
            log.warn("Can't mix Brooklyn entities with non-Brooklyn entities (at present): "+old);
            return false;
        }

        Builder<? extends PlatformComponentTemplate> builder = PlatformComponentTemplate.builder();
        
        if (item instanceof CatalogItem) {
            builder.type( "brooklyn:"+((CatalogItem<?>)item).getJavaType() );
        } else if (item instanceof Class) {
            builder.type( "brooklyn:"+((Class<?>) item).getCanonicalName() );
        } else {
            throw new IllegalStateException("Item "+item+" is not recognised here");
        }
        
        // currently instatiator must be brooklyn at the ATC level; optionally we support multiple?
        // and/or an instantiator at the component level, with the first one building the app
        atc.instantiator(BrooklynAssemblyTemplateInstantiator.class);
//        builder.instantiator(Brooklyn);
        
        // configuration
        Map<String, Object> attrs = MutableMap.copyOf( ((Service)deploymentPlanItem).getCustomAttributes() );
        Object brooklynConfig = attrs.remove("brooklyn.config");
        if (brooklynConfig!=null) {
            if (!(brooklynConfig instanceof Map))
                throw new IllegalArgumentException("brooklyn.config must be a map of brooklyn config keys");
            builder.customAttribute("brooklyn.config", brooklynConfig);
        }
        
        if (!attrs.isEmpty()) {
            log.warn("Ignoring PDP attributes on "+deploymentPlanItem+": "+attrs);
        }
        
        atc.add(builder.build());
        
        return true;
    }

}
