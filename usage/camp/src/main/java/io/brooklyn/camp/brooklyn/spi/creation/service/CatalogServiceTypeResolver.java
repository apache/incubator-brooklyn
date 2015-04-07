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
package io.brooklyn.camp.brooklyn.spi.creation.service;

import io.brooklyn.camp.spi.PlatformComponentTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.VanillaSoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicRegionsFabric;
import brooklyn.entity.proxying.EntitySpec;

/**
 * This converts {@link PlatformComponentTemplate} instances whose type is prefixed {@code catalog:}
 * to Brooklyn {@link EntitySpec} instances.
 */
public class CatalogServiceTypeResolver extends BrooklynServiceTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(ServiceTypeResolver.class);

    @Override
    public String getTypePrefix() { return "catalog"; }

    @Override
    public String getBrooklynType(String serviceType) {
        String type = super.getBrooklynType(serviceType);
        if (type == null) return null;

        // TODO currently a hardcoded list of aliases; would like that to come from mgmt somehow
        if (type.equals("cluster") || type.equals("Cluster")) return DynamicCluster.class.getName();
        if (type.equals("fabric") || type.equals("Fabric")) return DynamicRegionsFabric.class.getName();
        if (type.equals("vanilla") || type.equals("Vanilla")) return VanillaSoftwareProcess.class.getName();
        if (type.equals("web-app-cluster") || type.equals("WebAppCluster"))
            // TODO use service discovery; currently included as string to avoid needing a reference to it
            return "brooklyn.entity.webapp.ControlledDynamicWebAppCluster";

        return type;
    }

}
