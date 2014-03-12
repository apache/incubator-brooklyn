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
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.FlagUtils.FlagConfigKeyAndValueRecord;
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
                log.debug("Item "+serviceType+" not known in classpath ("+mgmt.getCatalog().getRootClassLoader()+"): "+e);
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
        
        String type;
        if (item instanceof CatalogItem) {
            type = ((CatalogItem<?>)item).getJavaType();
        } else if (item instanceof Class) {
            type = ((Class<?>) item).getName();
        } else {
            throw new IllegalStateException("Item "+item+" is not recognised here");
        }
        builder.type( "brooklyn:"+type );
        
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
        
        MutableMap<Object, Object> brooklynConfig = MutableMap.of();
        Object origBrooklynConfig = attrs.remove("brooklyn.config");
        if (origBrooklynConfig!=null) {
            if (!(origBrooklynConfig instanceof Map))
                throw new IllegalArgumentException("brooklyn.config must be a map of brooklyn config keys");
            brooklynConfig.putAll((Map<?,?>)origBrooklynConfig);
        }
        // also take *recognised* flags and config keys from the top-level, and put them under config
        List<FlagConfigKeyAndValueRecord> topLevelApparentConfig = extractValidConfigFlagsOrKeys(Strings.removeFromStart(type, "brooklyn:"), attrs, true);
        if (topLevelApparentConfig!=null) for (FlagConfigKeyAndValueRecord r: topLevelApparentConfig) {
            if (r.getConfigKeyMaybeValue().isPresent())
                brooklynConfig.put(r.getConfigKey(), r.getConfigKeyMaybeValue().get());
            if (r.getFlagMaybeValue().isPresent())
                brooklynConfig.put(r.getFlagName(), r.getFlagMaybeValue().get());
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
        
        List<Object> brooklynEnrichers = Lists.newArrayList();
        Object origBrooklynEnrichers = attrs.remove("brooklyn.enrichers");
        if (origBrooklynEnrichers != null) {
            if (!(origBrooklynEnrichers instanceof List))
                throw new IllegalArgumentException("brooklyn.enrichers must be a list of brooklyn enricher definitions");
            brooklynEnrichers.addAll((List<?>)origBrooklynEnrichers);
        }
        
        if (!brooklynEnrichers.isEmpty())
            builder.customAttribute("brooklyn.enrichers", brooklynEnrichers);
        
        List<Object> brooklynChildren = Lists.newArrayList();
        Object origBrooklynChildren = attrs.remove("brooklyn.children");
        if (origBrooklynChildren != null) {
            if (!(origBrooklynChildren instanceof List))
                throw new IllegalArgumentException("brooklyn.children must be a list of brooklyn entity definitions");
            brooklynChildren.addAll((List<?>)origBrooklynChildren);
        }
        
        if (!brooklynChildren.isEmpty())
            builder.customAttribute("brooklyn.children",  brooklynChildren);
        
        if (!attrs.isEmpty()) {
            log.warn("Ignoring PDP attributes on "+deploymentPlanItem+": "+attrs);
        }
        
        atc.add(builder.build());
        
        return true;
    }

    /** finds flags and keys on the given typeName which are present in the given map;
     * returns those (using the config key name), and removes them from attrs
     */
    protected List<FlagConfigKeyAndValueRecord> extractValidConfigFlagsOrKeys(String typeName, Map<String, Object> attrs, boolean removeIfFound) {
        if (attrs==null || attrs.isEmpty())
            return null;
        try {
            Class<?> type = Class.forName(typeName);
            ConfigBag bag = ConfigBag.newInstance(attrs);
            List<FlagConfigKeyAndValueRecord> values = FlagUtils.findAllFlagsAndConfigKeys(null, type, bag);
            
            if (removeIfFound) {
                // remove from attrs
                MutableMap<String, Object> used = MutableMap.copyOf(bag.getAllConfig());
                for (String unusedKey: bag.getUnusedConfig().keySet())
                    used.remove(unusedKey);
                for (String usedKey: used.keySet())
                    attrs.remove(usedKey);
            }
            
            return values;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Ignoring configuration attributes on "+typeName+" due to "+e, e);
            return null;
        }
    }

}
