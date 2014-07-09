package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import brooklyn.mementos.BrooklynMementoManifest;

import com.google.common.collect.Maps;

public class BrooklynMementoManifestImpl implements BrooklynMementoManifest, Serializable {

    private static final long serialVersionUID = -7424713724226824486L;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String brooklynVersion;
        protected final Map<String, String> entityIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> locationIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> policyIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> enricherIdToType = Maps.newConcurrentMap();
        
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return this;
        }
        public Builder entity(String id, String type) {
            entityIdToType.put(id, type); return this;
        }
        public Builder entities(Map<String, String> vals) {
            entityIdToType.putAll(vals); return this;
        }
        public Builder location(String id, String type) {
            locationIdToType.put(id, type); return this;
        }
        public Builder locations(Map<String, String> vals) {
            locationIdToType.putAll(vals); return this;
        }
        public Builder policy(String id, String type) {
            policyIdToType.put(id, type); return this;
        }
        public Builder policies(Map<String, String> vals) {
            policyIdToType.putAll(vals); return this;
        }
        public Builder enricher(String id, String type) {
            enricherIdToType.put(id, type); return this;
        }
        public Builder enrichers(Map<String, String> vals) {
            enricherIdToType.putAll(vals); return this;
        }
        public BrooklynMementoManifest build() {
            return new BrooklynMementoManifestImpl(this);
        }
    }

    private final Map<String, String> entityIdToType;
    private final Map<String, String> locationIdToType;
    private final Map<String, String> policyIdToType;
    private final Map<String, String> enricherIdToType;
    
    private BrooklynMementoManifestImpl(Builder builder) {
        entityIdToType = builder.entityIdToType;
        locationIdToType = builder.locationIdToType;
        policyIdToType = builder.policyIdToType;
        enricherIdToType = builder.enricherIdToType;
    }

    @Override
    public Map<String, String> getEntityIdToType() {
        return Collections.unmodifiableMap(entityIdToType);
    }

    @Override
    public Map<String, String> getLocationIdToType() {
        return Collections.unmodifiableMap(locationIdToType);
    }

    @Override
    public Map<String, String> getPolicyIdToType() {
        return Collections.unmodifiableMap(policyIdToType);
    }

    @Override
    public Map<String, String> getEnricherIdToType() {
        return Collections.unmodifiableMap(enricherIdToType);
    }
}
