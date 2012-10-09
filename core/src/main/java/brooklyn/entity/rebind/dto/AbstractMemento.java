package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import brooklyn.mementos.Memento;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public abstract class AbstractMemento implements Memento, Serializable {

    private static final long serialVersionUID = -8091049282749284567L;

    protected static abstract class Builder<B extends Builder<?>> {
        protected String id;
        protected String type;
        protected String displayName;
        public Map<String, Object> customProperties = Maps.newLinkedHashMap();
        
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B from(Memento other) {
            id = other.getId();
            type = other.getType();
            displayName = other.getDisplayName();
            customProperties.putAll(other.getCustomProperties());
            return self();
        }
        public B id(String val) {
            id = val; return self();
        }
        public B type(String val) {
            type = val; return self();
        }
        public B displayName(String val) {
            displayName = val; return self();
        }
        public B customProperties(Map<String,?> vals) {
            customProperties.putAll(vals); return self();
        }
    }
    
    private String id;
    private String type;
    private String displayName;
    private Map<String,Object> customProperties;
    
    // for de-serialization
    protected AbstractMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractMemento(Builder<?> builder) {
        id = builder.id;
        type = builder.type;
        displayName = builder.displayName;
        customProperties = Collections.unmodifiableMap(builder.customProperties );
    }

    @Override
    public String getId() {
        return id;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Object getCustomProperty(String name) {
        return customProperties.get(name);
    }
    
    public Map<String, ? extends Object> getCustomProperties() {
        return customProperties;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", getType()).add("id", getId()).toString();
    }
}
