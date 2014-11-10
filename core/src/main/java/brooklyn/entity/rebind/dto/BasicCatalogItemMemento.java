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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogBundle;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.mementos.CatalogItemMemento;

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

        public Builder from(CatalogItemMemento other) {
            super.from(other);
            description = other.getDescription();
            symbolicName = other.getSymbolicName();
            iconUrl = other.getIconUrl();
            javaType = other.getJavaType();
            version = other.getVersion();
            planYaml = other.getPlanYaml();
            libraries = other.getBundles();
            catalogItemType = other.getCatalogItemType();
            catalogItemJavaType = other.getCatalogItemJavaType();
            specType = other.getSpecType();
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
    //Keep libraries for deserialization compatibility and
    //introduce bundles to hold the new libraries type from
    //catalog item
    private Collection<CatalogItem.CatalogBundle> bundles;
    @SuppressWarnings("deprecation")
    private CatalogItem.CatalogItemLibraries libraries;
    private CatalogItem.CatalogItemType catalogItemType;
    private Class<?> catalogItemJavaType;
    private Class<?> specType;

    @SuppressWarnings("unused") // For deserialisation
    private BasicCatalogItemMemento() {}

    protected BasicCatalogItemMemento(Builder builder) {
        super(builder);
        this.description = builder.description;
        this.symbolicName = builder.symbolicName;
        this.iconUrl = builder.iconUrl;
        this.version = builder.version;
        this.planYaml = builder.planYaml;
        this.bundles = builder.libraries;
        this.libraries = null;
        this.catalogItemJavaType = builder.catalogItemJavaType;
        this.catalogItemType = builder.catalogItemType;
        this.specType = builder.specType;
        this.javaType = builder.javaType;
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
    public Collection<CatalogItem.CatalogBundle> getBundles() {
        if (bundles != null) {
            return bundles;
        } else if (libraries != null) {
            @SuppressWarnings("deprecation")
            Collection<CatalogBundle> b = libraries.getBundles();
            return b;
        } else {
            return null;
        }
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
                .add("bundles", getBundles())
                .add("catalogItemJavaType", getCatalogItemJavaType())
                .add("catalogItemType", getCatalogItemType())
                .add("javaType", getJavaType())
                .add("specType", getSpecType());
    }

}
