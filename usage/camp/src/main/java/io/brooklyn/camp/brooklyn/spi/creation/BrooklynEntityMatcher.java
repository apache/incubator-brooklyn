package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate.Builder;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.Service;
import io.brooklyn.camp.spi.resolve.PdpMatcher;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

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
        
        // currently instantiator must be brooklyn at the ATC level
        // optionally would be nice to support multiple/mixed instantiators, 
        // ie at the component level, perhaps with the first one responsible for building the app
        atc.instantiator(BrooklynAssemblyTemplateInstantiator.class);

        String name = ((Service)deploymentPlanItem).getName();
        if (!Strings.isBlank(name)) builder.name(name);
        
        // configuration
        Map<String, Object> attrs = MutableMap.copyOf( ((Service)deploymentPlanItem).getCustomAttributes() );

        if (attrs.containsKey("id"))
            builder.customAttribute("planId", attrs.remove("id"));

        Object location = attrs.remove("location");
        if (location!=null)
            builder.customAttribute("location", location);
        Object locations = attrs.remove("locations");
        if (locations!=null)
            builder.customAttribute("locations", locations);
        
        Map<Object, Object> brooklynConfig = MutableMap.of();
        Object origBrooklynConfig = attrs.remove("brooklyn.config");
        if (origBrooklynConfig!=null) {
            if (!(origBrooklynConfig instanceof Map))
                throw new IllegalArgumentException("brooklyn.config must be a map of brooklyn config keys");
            brooklynConfig.putAll((Map<?,?>)origBrooklynConfig);
        }
        // (any other brooklyn config goes here)
        if (!brooklynConfig.isEmpty())
            builder.customAttribute("brooklyn.config", brooklynConfig);
        
        List<Object> brooklynPolicies = Lists.newArrayList();
        Object origBrooklynPolicies = attrs.remove("brooklyn.policies");
        if (origBrooklynPolicies != null) {
            if (!(origBrooklynPolicies instanceof List))
                throw new IllegalArgumentException("brooklyn.policies must be a list of brooklyn policy definitions");
            brooklynPolicies.addAll((List<?>)origBrooklynPolicies);
        }
        
        if (!brooklynPolicies.isEmpty())
            builder.customAttribute("brooklyn.policies", brooklynPolicies);
        
        if (!attrs.isEmpty()) {
            log.warn("Ignoring PDP attributes on "+deploymentPlanItem+": "+attrs);
        }
        
        atc.add(builder.build());
        
        return true;
    }

}
