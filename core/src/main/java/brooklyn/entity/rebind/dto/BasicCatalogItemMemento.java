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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.mementos.CatalogItemMemento;

import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
public class BasicCatalogItemMemento extends AbstractMemento implements CatalogItemMemento, Serializable {

    private static final long serialVersionUID = -2040630288193425950L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected String description;
        protected String symbolicName;
        protected String iconUrl;
        protected String javaType;
        protected String version;
        protected String planYaml;
        protected Collection<CatalogItem.CatalogBundle> libraries;
        protected CatalogItem.CatalogItemType catalogItemType;
        protected Class<?> catalogItemJavaType;
        protected Class<?> specType;
        protected boolean deprecated;

        public Builder description(String description) {
            this.description = description;
            return self();
        }

        public Builder symbolicName(String symbolicName) {
            this.symbolicName = symbolicName;
            return self();
        }

        public Builder iconUrl(String iconUrl) {
            this.iconUrl = iconUrl;
            return self();
        }

        public Builder javaType(String javaType) {
            this.javaType = javaType;
            return self();
        }

        public Builder version(String version) {
            this.version = version;
            return self();
        }

        public Builder planYaml(String planYaml) {
            this.planYaml = planYaml;
            return self();
        }

        public Builder libraries(Collection<CatalogItem.CatalogBundle> libraries) {
            this.libraries = libraries;
            return self();
        }

        public Builder catalogItemType(CatalogItem.CatalogItemType catalogItemType) {
            this.catalogItemType = catalogItemType;
            return self();
        }

        public Builder catalogItemJavaType(Class<?> catalogItemJavaType) {
            this.catalogItemJavaType = catalogItemJavaType;
            return self();
        }

        public Builder specType(Class<?> specType) {
            this.specType = specType;
            return self();
        }

        public Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return self();
        }

        public Builder from(CatalogItemMemento other) {
            super.from(other);
            description = other.getDescription();
            symbolicName = other.getSymbolicName();
            iconUrl = other.getIconUrl();
            javaType = other.getJavaType();
            version = other.getVersion();
            planYaml = other.getPlanYaml();
            libraries = other.getLibraries();
            catalogItemType = other.getCatalogItemType();
            catalogItemJavaType = other.getCatalogItemJavaType();
            specType = other.getSpecType();
            deprecated = other.isDeprecated();
            return self();
        }

        public BasicCatalogItemMemento build() {
            return new BasicCatalogItemMemento(this);
        }
    }

    private String description;
    private String symbolicName;
    private String iconUrl;
    private String javaType;
    private String version;
    private String planYaml;
    private Collection<CatalogItem.CatalogBundle> libraries;
    private CatalogItem.CatalogItemType catalogItemType;
    private Class<?> catalogItemJavaType;
    private Class<?> specType;
    private boolean deprecated;

    @SuppressWarnings("unused") // For deserialisation
    private BasicCatalogItemMemento() {}

    protected BasicCatalogItemMemento(Builder builder) {
        super(builder);
        this.description = builder.description;
        this.symbolicName = builder.symbolicName;
        this.iconUrl = builder.iconUrl;
        this.version = builder.version;
        this.planYaml = builder.planYaml;
        this.libraries = builder.libraries;
        this.catalogItemJavaType = builder.catalogItemJavaType;
        this.catalogItemType = builder.catalogItemType;
        this.specType = builder.specType;
        this.javaType = builder.javaType;
        this.deprecated = builder.deprecated;
    }

    @Override
    public String getId() {
        return CatalogUtils.getVersionedId(getSymbolicName(), getVersion());
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        } else {
            return BasicBrooklynCatalog.NO_VERSION;
        }
    }

    @Override
    public String getPlanYaml() {
        return planYaml;
    }

    @Override
    public String getJavaType() {
        return javaType;
    }

    @Override
    public Collection<CatalogItem.CatalogBundle> getLibraries() {
        return libraries;
    }

    @Override
    public CatalogItem.CatalogItemType getCatalogItemType() {
        return catalogItemType;
    }

    @Override
    public Class<?> getCatalogItemJavaType() {
        return catalogItemJavaType;
    }

    @Override
    public Class<?> getSpecType() {
        return specType;
    }

    @Override
    public boolean isDeprecated() {
        return deprecated;
    }

    @Override
    protected void setCustomFields(Map<String, Object> fields) {
        if (!fields.isEmpty()) {
            throw new UnsupportedOperationException("Cannot set custom fields on " + this + ". " +
                    "Fields=" + Joiner.on(", ").join(fields.keySet()));
        }
    }

    @Override
    public Map<String, ? extends Object> getCustomFields() {
        return Collections.emptyMap();
    }

    @Override
    protected Objects.ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper()
                .add("description", getDescription())
                .add("symbolicName", getSymbolicName())
                .add("iconUrl", getIconUrl())
                .add("version", getVersion())
                .add("planYaml", getPlanYaml())
                .add("libraries", getLibraries())
                .add("catalogItemJavaType", getCatalogItemJavaType())
                .add("catalogItemType", getCatalogItemType())
                .add("javaType", getJavaType())
                .add("specType", getSpecType())
                .add("deprecated", isDeprecated());
    }

}
