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

import io.brooklyn.camp.spi.pdp.DeploymentPlan;

/** Deliberately package-private. Only for internal use. */
class CatalogItems {

    public static CatalogTemplateItemDto newTemplateFromJava(String javaType, String name) {
        return newTemplateFromJava(null, javaType, name, null);
    }
    public static CatalogTemplateItemDto newTemplateFromJava(String id, String javaType, String name, String description) {
        return newTemplateFromJava(id, javaType, name, description, null);
    }
    public static CatalogTemplateItemDto newTemplateFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogTemplateItemDto(), id, javaType, javaType, name, description, libraries);
    }

    public static CatalogEntityItemDto newEntityFromPlan(String registeredTypeName, CatalogLibrariesDto libraries, DeploymentPlan plan, String underlyingPlanYaml) {
        CatalogEntityItemDto target = set(new CatalogEntityItemDto(), null, registeredTypeName, null, plan.getName(), plan.getDescription(), libraries);
        target.planYaml = underlyingPlanYaml;
        return target;
    }
    
    public static CatalogEntityItemDto newEntityFromJava(String javaType, String name) {
        return newEntityFromJava(null, javaType, name, null);
    }
    public static CatalogEntityItemDto newEntityFromJava(String id, String javaType, String name, String description) {
        return newEntityFromJava(id, javaType, name, description, null);
    }
    public static CatalogEntityItemDto newEntityFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogEntityItemDto(), id, javaType, javaType, name, description, libraries);
    }

    public static CatalogPolicyItemDto newPolicyFromJava(String javaType, String name) {
        return newPolicyFromJava(null, javaType, name, null);
    }
    public static CatalogPolicyItemDto newPolicyFromJava(String id, String javaType, String name, String description) {
        return newPolicyFromJava(id, javaType, name, description, null);
    }
    public static CatalogPolicyItemDto newPolicyFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogPolicyItemDto(), id, javaType, javaType, name, description, libraries);
    }
 
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends CatalogItemDtoAbstract> T set(T target, String id, String registeredType, String javaType, String name,
            String description, CatalogLibrariesDto libraries) {
        target.id = id;
        target.registeredType = registeredType;
        target.javaType = javaType;
        target.name = name;
        target.description = description;
        target.libraries = libraries != null ? libraries : new CatalogLibrariesDto();
        return target;
    }

}
