package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.BrooklynVersion;
import brooklyn.mementos.Memento;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public abstract class AbstractMemento implements Memento, Serializable {

    private static final long serialVersionUID = -8091049282749284567L;

    protected static abstract class Builder<B extends Builder<?>> {
        protected String brooklynVersion = BrooklynVersion.get();
        protected String id;
        protected String type;
        protected String displayName;
        protected Map<String, Object> fields = Maps.newLinkedHashMap();
        
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B from(Memento other) {
            brooklynVersion = other.getBrooklynVersion();
            id = other.getId();
            type = other.getType();
            displayName = other.getDisplayName();
            fields.putAll(other.getCustomFields());
            return self();
        }
        public B brooklynVersion(String val) {
            brooklynVersion = val; return self();
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
        public B customFields(Map<String,?> vals) {
            fields.putAll(vals); return self();
        }
    }
    
    private String brooklynVersion;
    private String type;
    private String id;
    private String displayName;
    
    // for de-serialization
    protected AbstractMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractMemento(Builder<?> builder) {
        brooklynVersion = builder.brooklynVersion;
        id = builder.id;
        type = builder.type;
        displayName = builder.displayName;
        setCustomFields(builder.fields);
    }

    // "fields" is not included as a field here, so that it is serialized after selected subclass fields
    // but the method declared here simplifies how it is connected in via builder etc
    protected abstract void setCustomFields(Map<String, Object> fields);
    
    @Override
    public String getBrooklynVersion() {
        return brooklynVersion;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public Object getCustomField(String name) {
        if (getCustomFields()==null) return null;
        return getCustomFields().get(name);
    }
    
    @Override
    public abstract Map<String, ? extends Object> getCustomFields();
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", getType()).add("id", getId()).toString();
    }
    
    protected <T> List<T> fromPersistedList(List<T> l) {
        if (l==null) return Collections.emptyList();
        return Collections.unmodifiableList(l);
    }
    protected <T> List<T> toPersistedList(List<T> l) {
        if (l==null || l.isEmpty()) return null;
        return l;
    }
    protected <T> Set<T> fromPersistedSet(Set<T> l) {
        if (l==null) return Collections.emptySet();
        return Collections.unmodifiableSet(l);
    }
    protected <T> Set<T> toPersistedSet(Set<T> l) {
        if (l==null || l.isEmpty()) return null;
        return l;
    }
    protected <K,V> Map<K,V> fromPersistedMap(Map<K,V> m) {
        if (m==null) return Collections.emptyMap();
        return Collections.unmodifiableMap(m);
    }
    protected <K,V> Map<K,V> toPersistedMap(Map<K,V> m) {
        if (m==null || m.isEmpty()) return null;
        return m;
    }

}
