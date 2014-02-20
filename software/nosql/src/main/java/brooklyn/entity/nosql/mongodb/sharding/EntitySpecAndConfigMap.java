package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Map;

import org.testng.collections.Maps;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;

import com.google.common.base.Preconditions;

public class EntitySpecAndConfigMap<T extends Entity> {
    private EntitySpec<T> spec;
    private Map<?, ?> config;
    
    private EntitySpecAndConfigMap(){};
    
    private EntitySpecAndConfigMap(EntitySpec<T> spec, Map<?, ?> config) {
        this.spec = spec;
        this.config = config;
    }
    
    public EntitySpec<T> getSpec() {
        return spec;
    }
    
    public Map<?, ?> getConfigMap() {
        return config;
    }
    
    public static class Builder<T extends Entity> {
        private EntitySpec<T> spec;
        private Map<?, ?> config;
        public Builder<T> spec(EntitySpec<T> spec) {
            this.spec = spec;
            return this;
        }
        public Builder<T> config(Map<?, ?> config) {
            this.config = config;
            return this;
        }
        public EntitySpecAndConfigMap<T> build() {
            Preconditions.checkNotNull(spec, "spec");
            if (config == null)
                config = Maps.newHashMap();
            return new EntitySpecAndConfigMap<T>(spec, config);
        }
    }
}
