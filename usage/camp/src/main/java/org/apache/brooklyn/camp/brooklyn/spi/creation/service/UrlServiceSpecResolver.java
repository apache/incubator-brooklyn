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

import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampTypePlanTransformer;
import org.apache.brooklyn.core.resolve.entity.EntitySpecResolver;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.net.Urls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Specific to CAMP because linked plans are assumed to be CAMP format. No type discovery available. */
public class UrlServiceSpecResolver implements EntitySpecResolver {
    private static final Logger log = LoggerFactory.getLogger(UrlServiceSpecResolver.class);

    @Override
    public String getName() {
        return "url";
    }

    @Override
    public boolean accepts(String type, BrooklynClassLoadingContext loader) {
        String protocol = Urls.getProtocol(type);
        return protocol != null &&
            BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST.contains(protocol);
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        String yaml;
        try {
            yaml = ResourceUtils.create(this).getResourceAsString(type);
        } catch (Exception e) {
            log.warn("AssemblyTemplate type " + type + " looks like a URL that can't be fetched.", e);
            return null;
        }
        if (encounteredTypes.contains(type)) {
            throw new IllegalStateException("URL " + type + " is self referential.");
        }
        
        // Referenced specs are expected to be CAMP format as well.
        // TODO somehow specify to allow full syntax for services
        EntitySpec<?> item = loader.getManagementContext().getTypeRegistry().createSpecFromPlan(
            CampTypePlanTransformer.FORMAT,
            yaml,
            RegisteredTypeLoadingContexts.loaderAlreadyEncountered(loader, encounteredTypes, type), 
            EntitySpec.class);
        return item;
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
    }

}
