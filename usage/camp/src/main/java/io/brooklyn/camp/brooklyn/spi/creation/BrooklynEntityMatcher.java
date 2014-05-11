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

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.FlagUtils.FlagConfigKeyAndValueRecord;
import brooklyn.util.text.Strings;

import com.google.common.collect.Lists;

public class BrooklynEntityMatcher implements PdpMatcher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynEntityMatcher.class);
    
    protected final ManagementContext mgmt;

    public BrooklynEntityMatcher(ManagementContext bmc) {
        this.mgmt = bmc;
    }

    @Override
    public boolean accepts(Object deploymentPlanItem) {
        return lookupType(deploymentPlanItem) != null;
    }

    /** returns the type of the given plan item, 
     * typically whether a Service can be matched to a Brooklyn entity,
     * or null if not supported */
    protected String lookupType(Object deploymentPlanItem) {
        if (deploymentPlanItem instanceof Service) {
            if (!BrooklynComponentTemplateResolver.Factory.supportsType(mgmt, ((Service)deploymentPlanItem).getServiceType()))
                return null;
            return ((Service)deploymentPlanItem).getServiceType();
        }
        return null;
    }

    @Override
    public boolean apply(Object deploymentPlanItem, AssemblyTemplateConstructor atc) {
        if (!(deploymentPlanItem instanceof Service)) return false;
        
        String type = lookupType(deploymentPlanItem);
        if (type==null) return false;

        log.debug("Item "+deploymentPlanItem+" being instantiated with "+type);

        Object old = atc.getInstantiator();
        if (old!=null && !old.equals(BrooklynAssemblyTemplateInstantiator.class)) {
            log.warn("Can't mix Brooklyn entities with non-Brooklyn entities (at present): "+old);
            return false;
        }

        // TODO should we build up a new type, BrooklynEntityComponentTemplate here
        // complete w EntitySpec -- ie merge w BrooklynComponentTemplateResolver ?
        
        Builder<? extends PlatformComponentTemplate> builder = PlatformComponentTemplate.builder();
        builder.type( type.indexOf(':')==-1 ? "brooklyn:"+type : type );
        
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
        List<FlagConfigKeyAndValueRecord> topLevelApparentConfig = extractValidConfigFlagsOrKeys(type, attrs, true);
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

        List<Object> brooklynInitializers = Lists.newArrayList();
        Object origBrooklynInitializers = attrs.remove("brooklyn.initializers");
        if (origBrooklynInitializers != null) {
            if (!(origBrooklynInitializers instanceof List))
                throw new IllegalArgumentException("brooklyn.initializers must be a list of brooklyn initializer definitions");
            brooklynInitializers.addAll((List<?>)origBrooklynInitializers);
        }
        if (!brooklynInitializers.isEmpty())
            builder.customAttribute("brooklyn.initializers", brooklynInitializers);

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
            Class<Entity> type = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, typeName).loadEntityClass();
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
