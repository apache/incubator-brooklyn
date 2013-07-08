package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

public class CatalogEntitySummary extends CatalogItemSummary {

    private final Set<EntityConfigSummary> config;
    private final Set<SensorSummary> sensors;
    private final Set<EffectorSummary> effectors;

    public CatalogEntitySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("config") Set<EntityConfigSummary> config, 
            @JsonProperty("sensors") Set<SensorSummary> sensors, 
            @JsonProperty("effectors") Set<EffectorSummary> effectors,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(id, name, type, description, iconUrl, links);
        this.config = config;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public Set<EntityConfigSummary> getConfig() {
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
