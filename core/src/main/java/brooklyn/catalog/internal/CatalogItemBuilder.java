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
package brooklyn.catalog.internal;

import com.google.common.base.Preconditions;

public class CatalogItemBuilder<CatalogItemType extends CatalogItemDtoAbstract<?, ?>> {
    private CatalogItemType dto;

    public static CatalogItemBuilder<CatalogEntityItemDto> newEntity(String registeredTypeName) {
        return new CatalogItemBuilder<CatalogEntityItemDto>(new CatalogEntityItemDto())
                .registeredTypeName(registeredTypeName);
    }

    public static CatalogItemBuilder<CatalogTemplateItemDto> newTemplate() {
        return new CatalogItemBuilder<CatalogTemplateItemDto>(new CatalogTemplateItemDto());
    }

    public static CatalogItemBuilder<CatalogPolicyItemDto> newPolicy(String registeredTypeName) {
        return new CatalogItemBuilder<CatalogPolicyItemDto>(new CatalogPolicyItemDto())
                .registeredTypeName(registeredTypeName);
    }

    public CatalogItemBuilder(CatalogItemType dto) {
        this.dto = dto;
        this.dto.libraries = new CatalogLibrariesDto();
    }

    public CatalogItemBuilder<CatalogItemType> registeredTypeName(String registeredType) {
        dto.registeredType = registeredType;
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> name(String name) {
        dto.name = name;
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> description(String description) {
        dto.description = description;
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> iconUrl(String iconUrl) {
        dto.iconUrl = iconUrl;
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> libraries(CatalogLibrariesDto libraries) {
        dto.libraries = libraries;
        return this;
    }

    public CatalogItemBuilder<CatalogItemType> plan(String yaml) {
        dto.planYaml = yaml;
        return this;
    }

    public CatalogItemType build() {
        Preconditions.checkNotNull(dto.registeredType);

        if (dto.libraries == null) {
            dto.libraries = new CatalogLibrariesDto();
        }

        CatalogItemType ret = dto;

        //prevent mutations through the builder
        dto = null;

        return ret;
    }

}
