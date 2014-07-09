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

import java.util.List;
import java.util.Map;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.util.xstream.EnumCaseForgivingSingleValueConverter;
import brooklyn.util.xstream.XmlSerializer;

public class CatalogXmlSerializer extends XmlSerializer<Object> {

    public CatalogXmlSerializer() {
        xstream.aliasType("list", List.class);
        xstream.aliasType("map", Map.class);

        xstream.useAttributeFor("id", String.class);

        xstream.aliasType("catalog", CatalogDto.class);
        xstream.useAttributeFor(CatalogDto.class, "url");
        xstream.addImplicitCollection(CatalogDto.class, "catalogs", CatalogDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogTemplateItemDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogEntityItemDto.class);
        xstream.addImplicitCollection(CatalogDto.class, "entries", CatalogPolicyItemDto.class);

        xstream.aliasType("template", CatalogTemplateItemDto.class);
        xstream.aliasType("entity", CatalogEntityItemDto.class);
        xstream.aliasType("policy", CatalogPolicyItemDto.class);

        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "type");
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "name");
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "version");

        xstream.useAttributeFor(CatalogClasspathDto.class, "scan");
        xstream.addImplicitCollection(CatalogClasspathDto.class, "entries", "entry", String.class);
        xstream.registerConverter(new EnumCaseForgivingSingleValueConverter(CatalogScanningModes.class));

        xstream.aliasType("libraries", CatalogLibrariesDto.class);
        xstream.addImplicitCollection(CatalogLibrariesDto.class, "bundles", "bundle", String.class);
    }

}
