package brooklyn.cli.itemlister;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.basic.BrooklynDynamicType;
import brooklyn.basic.BrooklynObject;
import brooklyn.basic.BrooklynType;
import brooklyn.basic.BrooklynTypes;
import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.event.Sensor;
import brooklyn.location.LocationResolver;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.domain.SummaryComparators;
import brooklyn.rest.transform.EffectorTransformer;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.SensorTransformer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ItemDescriptors {

    public static List<Map<String, Object>> toItemDescriptors(Iterable<? extends Class<? extends BrooklynObject>> types, boolean headingsOnly) {
        List<Map<String, Object>> itemDescriptors = Lists.newArrayList();
        
        for (Class<? extends BrooklynObject> type : types) {
            Map<String, Object> itemDescriptor = toItemDescriptor(type, headingsOnly);
            itemDescriptors.add(itemDescriptor);
        }
        
        return itemDescriptors;
    }
    
    public static Map<String,Object> toItemDescriptor(Class<? extends BrooklynObject> clazz, boolean headingsOnly) {
        BrooklynDynamicType<?, ?> dynamicType = BrooklynTypes.getDefinedBrooklynType(clazz);
        BrooklynType type = dynamicType.getSnapshot();
        ConfigKey<?> version = dynamicType.getConfigKey(BrooklynConfigKeys.SUGGESTED_VERSION.getName());
        
        Map<String,Object> result = Maps.newLinkedHashMap();
        
        result.put("type", clazz.getName());
        if (version != null) {
            result.put("defaultVersion", version.getDefaultValue());
        }
        
        Catalog catalogAnnotation = clazz.getAnnotation(Catalog.class);
        if (catalogAnnotation != null) {
            result.put("name", catalogAnnotation.name());
            result.put("description", catalogAnnotation.description());
            result.put("iconUrl", catalogAnnotation.iconUrl());
        }
        
        Deprecated deprecatedAnnotation = clazz.getAnnotation(Deprecated.class);
        if (deprecatedAnnotation != null) {
            result.put("deprecated", true);
        }
        
        if (!headingsOnly) {
            Set<EntityConfigSummary> config = Sets.newTreeSet(SummaryComparators.nameComparator());
            Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
            Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

            for (ConfigKey<?> x: type.getConfigKeys()) {
                config.add(EntityTransformer.entityConfigSummary(x, dynamicType.getConfigKeyField(x.getName())));
            }
            result.put("config", config);
            
            if (type instanceof EntityType) {
                for (Sensor<?> x: ((EntityType)type).getSensors())
                    sensors.add(SensorTransformer.sensorSummaryForCatalog(x));
                result.put("sensors", sensors);
                
                for (Effector<?> x: ((EntityType)type).getEffectors())
                    effectors.add(EffectorTransformer.effectorSummaryForCatalog(x));
                result.put("effectors", effectors);
            }
        }
        
        return result;
    }
    
    public static Object toItemDescriptors(List<LocationResolver> resolvers) {
        List<Object> result = Lists.newArrayList();
        for (LocationResolver resolver : resolvers) {
            result.add(toItemDescriptor(resolver));
        }
        return result;
    }

    public static Object toItemDescriptor(LocationResolver resolver) {
        // TODO Get javadoc of LocationResolver? Could use docklet? But that would give dependency here
        // on com.sun.javadoc.*
        return resolver.getPrefix();
    }
}
