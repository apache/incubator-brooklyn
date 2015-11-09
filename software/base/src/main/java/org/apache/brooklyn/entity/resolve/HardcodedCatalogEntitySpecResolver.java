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
package org.apache.brooklyn.entity.resolve;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.entity.AbstractEntitySpecResolver;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicRegionsFabric;
import org.apache.brooklyn.entity.java.VanillaJavaApp;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;

public class HardcodedCatalogEntitySpecResolver extends AbstractEntitySpecResolver {
    private static final String RESOLVER_NAME = "catalog";

    private static final Map<String, String> CATALOG_TYPES = ImmutableMap.<String, String>builder()
            .put("cluster", DynamicCluster.class.getName())
            .put("fabric", DynamicRegionsFabric.class.getName())
            .put("vanilla", VanillaSoftwareProcess.class.getName())
            .put("software-process", VanillaSoftwareProcess.class.getName())
            .put("java-app", VanillaJavaApp.class.getName())
            .put("brooklyn-node", BrooklynNode.class.getName())
            .put("web-app-cluster","org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster")
            .build();

    // Allow catalog-type or CatalogType as service type string
    private static final Converter<String, String> FMT = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_HYPHEN);

    public HardcodedCatalogEntitySpecResolver() {
        super(RESOLVER_NAME);
    }

    @Override
    protected boolean canResolve(String type, BrooklynClassLoadingContext loader) {
        String localType = getLocalType(type);
        String specType = getImplementation(localType);
        return specType != null;
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        String localType = getLocalType(type);
        String specType = getImplementation(localType);
        if (specType != null) {
            return buildSpec(specType);
        } else {
            return null;
        }
    }

    private String getImplementation(String type) {
        String specType = CATALOG_TYPES.get(type);
        if (specType != null) {
            return specType;
        } else {
            return CATALOG_TYPES.get(FMT.convert(type));
        }
    }

    private EntitySpec<?> buildSpec(String specType) {
        // TODO is this hardcoded list deprecated? If so log a warning.
        try {
            @SuppressWarnings("unchecked")
            Class<Entity> specClass = (Class<Entity>)mgmt.getCatalogClassLoader().loadClass(specType);
            return EntitySpec.create(specClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load hardcoded catalog type " + specType, e);
        }
    }

}
