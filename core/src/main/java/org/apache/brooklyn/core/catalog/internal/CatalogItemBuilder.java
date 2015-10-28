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
package org.apache.brooklyn.core.catalog.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogInput;

import com.google.common.base.Preconditions;

public class CatalogItemBuilder<CatalogItemType extends CatalogItemDtoAbstract<?, ?>> {
    private CatalogItemType dto;

    public static CatalogItemBuilder<CatalogEntityItemDto> newEntity(String symbolicName, String version) {
        return new CatalogItemBuilder<CatalogEntityItemDto>(new CatalogEntityItemDto())
                .symbolicName(symbolicName)
                .version(version);
    }

    public static CatalogItemBuilder<CatalogTemplateItemDto> newTemplate(String symbolicName, String version) {
        return new CatalogItemBuilder<CatalogTemplateItemDto>(new CatalogTemplateItemDto())
                .symbolicName(symbolicName)
                .version(version);
    }

    public static CatalogItemBuilder<CatalogPolicyItemDto> newPolicy(String symbolicName, String version) {
        return new CatalogItemBuilder<CatalogPolicyItemDto>(new CatalogPolicyItemDto())
                .symbolicName(symbolicName)
                .version(version);
    }

    public static CatalogItemBuilder<CatalogLocationItemDto> newLocation(String symbolicName, String version) {
        return new CatalogItemBuilder<CatalogLocationItemDto>(new CatalogLocationItemDto())
                .symbolicName(symbolicName)
                .version(version);
    }

    public CatalogItemBuilder(CatalogItemType dto) {
        this.dto = dto;
        this.dto.setLibraries(Collections.<CatalogBundle>emptyList());
    }

    public CatalogItemBuilder<CatalogItemType> symbolicName(String symbolicName) {
        dto.setSymbolicName(symbolicName);
        return this;
    }

    @Deprecated
    public CatalogItemBuilder<CatalogItemType> javaType(String javaType) {
        dto.setJavaType(javaType);
        return this;
    }

    /** @deprecated since 0.7.0 use {@link #displayName}*/
    @Deprecated
    public CatalogItemBuilder<CatalogItemType> name(String name) {
        return displayName(name);
    }

    public CatalogItemBuilder<CatalogItemType> displayName(String displayName) {
        dto.setDisplayName(displayName);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> description(String description) {
        dto.setDescription(description);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> iconUrl(String iconUrl) {
        dto.setIconUrl(iconUrl);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> version(String version) {
        dto.setVersion(version);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> deprecated(boolean deprecated) {
        dto.setDeprecated(deprecated);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> disabled(boolean disabled) {
        dto.setDisabled(disabled);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> inputs(List<CatalogInput<?>> inputs) {
        dto.setInputs(inputs);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> libraries(Collection<CatalogBundle> libraries) {
        dto.setLibraries(libraries);
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> plan(String yaml) {
        dto.setPlanYaml(yaml);
        return this;
    }

    public CatalogItemType build() {
        Preconditions.checkNotNull(dto.getSymbolicName());
        Preconditions.checkNotNull(dto.getVersion());

        if (dto.getInputs() == null) {
            dto.setInputs(Collections.<CatalogInput<?>>emptyList());
        }
        if (dto.getLibraries() == null) {
            dto.setLibraries(Collections.<CatalogBundle>emptyList());
        }

        CatalogItemType ret = dto;

        //prevent mutations through the builder
        dto = null;

        return ret;
    }

}
