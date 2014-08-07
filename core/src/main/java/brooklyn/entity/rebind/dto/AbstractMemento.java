/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.Entities;
import brooklyn.mementos.Memento;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class AbstractMemento implements Memento, Serializable {

    private static final long serialVersionUID = -8091049282749284567L;

    protected static abstract class Builder<B extends Builder<?>> {
        protected String brooklynVersion = BrooklynVersion.get();
        protected String id;
        protected String type;
        protected Class<?> typeClass;
        protected String displayName;
        protected Map<String, Object> fields = Maps.newLinkedHashMap();
        protected List<Object> tags = Lists.newArrayList();

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B from(Memento other) {
            brooklynVersion = other.getBrooklynVersion();
            id = other.getId();
            type = other.getType();
            typeClass = other.getTypeClass();
            displayName = other.getDisplayName();
            fields.putAll(other.getCustomFields());
            tags.addAll(other.getTags());
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
        public B typeClass(Class<?> val) {
            typeClass = val; return self();
        }
        public B displayName(String val) {
            displayName = val; return self();
        }
        /**
         * @deprecated since 0.7.0; use config/attributes so generic persistence will work, rather than requiring "custom fields"
         */
        @Deprecated
        public B customFields(Map<String,?> vals) {
            fields.putAll(vals); return self();
        }
    }
    
    private String brooklynVersion;
    private String type;
    private String id;
    private String displayName;
    private List<Object> tags;

    private transient Class<?> typeClass;

    // for de-serialization
    protected AbstractMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractMemento(Builder<?> builder) {
        brooklynVersion = builder.brooklynVersion;
        id = builder.id;
        type = builder.type;
        typeClass = builder.typeClass;
        displayName = builder.displayName;
        setCustomFields(builder.fields);
        tags = toPersistedList(builder.tags);
    }

    // "fields" is not included as a field here, so that it is serialized after selected subclass fields
    // but the method declared here simplifies how it is connected in via builder etc
    protected abstract void setCustomFields(Map<String, Object> fields);
    
    @Override
    public void injectTypeClass(Class<?> clazz) {
        this.typeClass = clazz;
    }
    
    @Override
    public Class<?> getTypeClass() {
        return typeClass;
    }
    
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

    public List<Object> getTags() {
        return fromPersistedList(tags);
    }
    
    @Deprecated
    @Override
    public Object getCustomField(String name) {
        if (getCustomFields()==null) return null;
        return getCustomFields().get(name);
    }
    
    @Deprecated
    @Override
    public abstract Map<String, ? extends Object> getCustomFields();
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", getType()).add("id", getId()).toString();
    }
    
    @Override
    public String toVerboseString() {
        return newVerboseStringHelper().toString();
    }
    
    protected ToStringHelper newVerboseStringHelper() {
        return Objects.toStringHelper(this).add("id", getId()).add("type", getType())
                .add("displayName", getDisplayName()).add("customFields", Entities.sanitize(getCustomFields()));
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
