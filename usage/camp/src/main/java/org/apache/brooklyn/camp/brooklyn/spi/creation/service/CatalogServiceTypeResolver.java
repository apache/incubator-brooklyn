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
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import io.brooklyn.camp.spi.PlatformComponentTemplate;

import java.util.Map;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.VanillaSoftwareProcess;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicRegionsFabric;
import brooklyn.entity.java.VanillaJavaApp;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;

/**
 * This converts {@link PlatformComponentTemplate} instances whose type is prefixed {@code catalog:}
 * to Brooklyn {@link EntitySpec} instances.
 */
public class CatalogServiceTypeResolver extends BrooklynServiceTypeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceTypeResolver.class);

    // TODO currently a hardcoded list of aliases; would like that to come from mgmt somehow
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
    private static final Converter<String, String> FMT = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_CAMEL);

    @Override
    public String getTypePrefix() { return "catalog"; }

    @Override
    public String getBrooklynType(String serviceType) {
        String type = super.getBrooklynType(serviceType);
        if (type == null) return null;

        for (String check : CATALOG_TYPES.keySet()) {
            if (type.equals(check) || type.equals(FMT.convert(check))) {
                return CATALOG_TYPES.get(check);
            }
        }

        return type;
    }

}
