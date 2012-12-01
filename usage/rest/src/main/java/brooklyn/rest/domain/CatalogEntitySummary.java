package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.event.Sensor;
import brooklyn.rest.util.BrooklynRestResourceUtils;

import com.google.common.collect.Sets;

public class CatalogEntitySummary extends CatalogItemSummary {

    private final Set<ConfigSummary> config;
    private final Set<SensorSummary> sensors;
    private final Set<EffectorSummary> effectors;

    public CatalogEntitySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("config") Set<ConfigSummary> config, 
            @JsonProperty("sensors") Set<SensorSummary> sensors, 
            @JsonProperty("effectors") Set<EffectorSummary> effectors,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(id, name, type, description, iconUrl, links);
        this.config = config;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public static CatalogEntitySummary from(BrooklynRestResourceUtils b, CatalogItem<? extends Entity> item) {
        EntityType type = EntityTypes.getDefinedEntityType(b.getCatalog().loadClass(item)).getSnapshot();
        
        Set<ConfigSummary> config = Sets.newLinkedHashSet();
        Set<SensorSummary> sensors = Sets.newLinkedHashSet();
        Set<EffectorSummary> effectors = Sets.newLinkedHashSet();
        
        for (ConfigKey<?> x: type.getConfigKeys()) config.add(ConfigSummary.forCatalog(x));
        for (Sensor<?> x: type.getSensors()) sensors.add(SensorSummary.forCatalog(x));
        for (Effector<?> x: type.getEffectors()) effectors.add(EffectorSummary.forCatalog(x));

        return new CatalogEntitySummary(item.getId(), item.getName(), item.getJavaType(), 
                item.getDescription(), item.getIconUrl(), 
                config, sensors, effectors,
                makeLinks(item));
    }
    
    public Set<ConfigSummary> getConfig() {
        return config;
    }
    
    public Set<SensorSummary> getSensors() {
        return sensors;
    }
    
    public Set<EffectorSummary> getEffectors() {
        return effectors;
    }

    @Override
    public String toString() {
        return super.toString()+"["+
                "config="+getConfig()+"; " +
        		"sensors="+getSensors()+"; "+
        		"effectors="+getEffectors()+"]";
    }
}
