package brooklyn.rest.api;

import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.event.Sensor;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class CatalogEntitySummary {

    private final Set<ConfigSummary> config;
    private final Set<SensorSummary> sensors;
    private final Set<EffectorSummary> effectors;

    public CatalogEntitySummary(
            @JsonProperty("config") Set<ConfigSummary> config, 
            @JsonProperty("sensors") Set<SensorSummary> sensors, 
            @JsonProperty("effectors") Set<EffectorSummary> effectors) {
        this.config = config;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public static CatalogEntitySummary fromType(Class<? extends Entity> entityType) {
        return fromType(EntityTypes.getDefinedEntityType(entityType));
    }
    public static CatalogEntitySummary fromType(EntityDynamicType type) {
        return fromType(type.getSnapshot());
    }
    public static CatalogEntitySummary fromType(EntityType type) {
        Set<ConfigSummary> config = Sets.newLinkedHashSet();
        Set<SensorSummary> sensors = Sets.newLinkedHashSet();
        Set<EffectorSummary> effectors = Sets.newLinkedHashSet();
        
        for (ConfigKey<?> x: type.getConfigKeys()) config.add(ConfigSummary.forCatalog(x));
        for (Sensor<?> x: type.getSensors()) sensors.add(SensorSummary.forCatalog(x));
        for (Effector<?> x: type.getEffectors()) effectors.add(EffectorSummary.forCatalog(x));
        
        return new CatalogEntitySummary(ImmutableSet.copyOf(config), ImmutableSet.copyOf(sensors), ImmutableSet.copyOf(effectors));
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
        // TODO spit out as json?
        return super.toString()+"["+
                "config="+getConfig()+"; " +
        		"sensors="+getSensors()+"; "+
        		"effectors="+getEffectors()+"]";
    }
}
