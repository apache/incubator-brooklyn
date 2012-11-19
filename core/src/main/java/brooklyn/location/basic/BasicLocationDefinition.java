package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.LocationDefinition;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class BasicLocationDefinition implements LocationDefinition {

    private final String id;
    private final String name;
    private final String spec;
    private final Map<String,Object> config;

    public BasicLocationDefinition(String id, String name, String spec, Map<String,? extends Object> config) {      
        this.id = id != null ? id : LanguageUtils.newUid();
        this.name = name;
        this.spec = Preconditions.checkNotNull(spec);
        this.config = config==null ? ImmutableMap.<String, Object>of() : ImmutableMap.<String, Object>copyOf(config);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public String getSpec() {
        return spec;
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return config;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this==o) return true;
        if ((o instanceof LocationDefinition) && id.equals(((LocationDefinition)o).getId())) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LocationDefinition{" +
                "id='" + getId() + '\'' +
                ", name='" + getName() + '\'' +
                ", spec='" + getSpec() + '\'' +
                ", config=" + getConfig() +
                '}';
    }
}
