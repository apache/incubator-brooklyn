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
        protected final Map<String, String> entityIdToType = Maps.newLinkedHashMap();
        protected final Map<String, String> locationIdToType = Maps.newLinkedHashMap();
        protected final Map<String, String> policyIdToType = Maps.newLinkedHashMap();
        
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
        public BrooklynMementoManifest build() {
            return new BrooklynMementoManifestImpl(this);
        }
    }

    private Map<String, String> entityIdToType;
    private Map<String, String> locationIdToType;
    private Map<String, String> policyIdToType;
    
    private BrooklynMementoManifestImpl(Builder builder) {
        entityIdToType = builder.entityIdToType;
        locationIdToType = builder.locationIdToType;
        policyIdToType = builder.policyIdToType;
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
}
