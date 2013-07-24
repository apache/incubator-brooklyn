package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.event.Sensor;
import brooklyn.policy.Policy;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.domain.CatalogPolicySummary;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.PolicyConfigSummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class CatalogTransformer {

    @SuppressWarnings("unused")
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CatalogTransformer.class);
    
    public static CatalogEntitySummary catalogEntitySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Entity> item) {
        Class<? extends Entity> clazz = b.getCatalog().loadClass(item);
        EntityDynamicType typeMap = EntityTypes.getDefinedEntityType(clazz);
        EntityType type = typeMap.getSnapshot();

        Set<EntityConfigSummary> config = Sets.newLinkedHashSet();
        Set<SensorSummary> sensors = Sets.newLinkedHashSet();
        Set<EffectorSummary> effectors = Sets.newLinkedHashSet();

        for (ConfigKey<?> x: type.getConfigKeys()) config.add(EntityTransformer.entityConfigSummary(x, typeMap.getConfigKeyField(x.getName())));
        for (Sensor<?> x: type.getSensors()) sensors.add(SensorTransformer.sensorSummaryForCatalog(x));
        for (Effector<?> x: type.getEffectors()) effectors.add(EffectorTransformer.effectorSummaryForCatalog(x));

        return new CatalogEntitySummary(item.getId(), item.getName(), item.getJavaType(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()),
                config, sensors, effectors,
                makeLinks(item));
    }

    public static CatalogItemSummary catalogItemSummary(BrooklynRestResourceUtils b, CatalogItem<?> item) {
        return new CatalogItemSummary(item.getId(), item.getName(), item.getJavaType(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), makeLinks(item));
    }

    public static CatalogPolicySummary catalogPolicySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Policy> item) {
        Set<PolicyConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getId(), item.getName(), item.getJavaType(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                makeLinks(item));
    }

    protected static Map<String, URI> makeLinks(CatalogItem<?> item) {
        return MutableMap.<String, URI>of();
    }
    
    private static String tidyIconLink(BrooklynRestResourceUtils b, CatalogItem<?> item, String iconUrl) {
        if (b.isUrlServerSideAndSafe(iconUrl))
            return "/v1/catalog/icon/"+item.getId();
        return iconUrl;
    }

}
