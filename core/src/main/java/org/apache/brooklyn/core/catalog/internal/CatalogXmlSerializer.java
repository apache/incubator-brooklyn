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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.core.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import org.apache.brooklyn.core.util.xstream.EnumCaseForgivingSingleValueConverter;
import org.apache.brooklyn.core.util.xstream.XmlSerializer;

import org.apache.brooklyn.basic.AbstractBrooklynObject;
import brooklyn.util.xstream.EnumCaseForgivingSingleValueConverter;
import brooklyn.util.xstream.XmlSerializer;

public class CatalogXmlSerializer extends XmlSerializer<Object> {

    @SuppressWarnings("deprecation")
    public CatalogXmlSerializer() {
        xstream.addDefaultImplementation(ArrayList.class, Collection.class);
        
        xstream.aliasType("list", List.class);
        xstream.aliasType("map", Map.class);

        xstream.useAttributeFor("id", String.class);

        xstream.aliasType("catalog", CatalogDto.class);
        xstream.useAttributeFor(CatalogDto.class, "url");
        xstream.addImplicitCollection(CatalogDto.class, "catalogs", CatalogDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogTemplateItemDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogEntityItemDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogPolicyItemDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogLocationItemDto.class);

        xstream.aliasType("template", CatalogTemplateItemDto.class);
        xstream.aliasType("entity", CatalogEntityItemDto.class);
        xstream.aliasType("policy", CatalogPolicyItemDto.class);
        xstream.aliasType("location", CatalogPolicyItemDto.class);

        xstream.aliasField("registeredType", CatalogItemDtoAbstract.class, "symbolicName");
        xstream.aliasAttribute(CatalogItemDtoAbstract.class, "displayName", "name");
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "type");
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "version");
        xstream.aliasType("bundle", CatalogBundleDto.class);
        xstream.registerConverter(new CatalogBundleConverter(xstream.getMapper(), xstream.getReflectionProvider()));

        xstream.useAttributeFor(CatalogClasspathDto.class, "scan");
        xstream.addImplicitCollection(CatalogClasspathDto.class, "entries", "entry", String.class);
        xstream.registerConverter(new EnumCaseForgivingSingleValueConverter(CatalogScanningModes.class));

        // Note: the management context is being omitted because it is unnecessary for
        // representations of catalogues generated with this serializer.
        xstream.omitField(AbstractBrooklynObject.class, "managementContext");
        xstream.omitField(AbstractBrooklynObject.class, "_legacyConstruction");
        xstream.omitField(AbstractBrooklynObject.class, "hasWarnedOfNoManagementContextWhenPersistRequested");
    }

}
