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
package org.apache.brooklyn.core.mgmt.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigPredicates;
import org.apache.brooklyn.core.config.ConfigUtils;
import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Simple registry implementation.
 *
 * Permits a number of {@link ExternalConfigSupplier} instances to be registered, each with a unique name, for future
 * (deferred) lookup of configuration values.
 */
public class BasicExternalConfigSupplierRegistry implements ExternalConfigSupplierRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BasicExternalConfigSupplierRegistry.class);

    private final Map<String, ExternalConfigSupplier> providersByName = Maps.newLinkedHashMap();
    private final Object providersMapMutex = new Object();

    public BasicExternalConfigSupplierRegistry(ManagementContext mgmt) {
        updateFromBrooklynProperties(mgmt);
    }

    @Override
    public void addProvider(String name, ExternalConfigSupplier supplier) {
        synchronized (providersMapMutex) {
            if (providersByName.containsKey(name))
                throw new IllegalArgumentException("Provider already registered with name '" + name + "'");
            providersByName.put(name, supplier);
        }
        LOG.info("Added external config supplier named '" + name + "': " + supplier);
    }

    @Override
    public void removeProvider(String name) {
        synchronized (providersMapMutex) {
            ExternalConfigSupplier supplier = providersByName.remove(name);
            LOG.info("Removed external config supplier named '" + name + "': " + supplier);
        }
    }

    @Override
    public String getConfig(String providerName, String key) {
        synchronized (providersMapMutex) {
            ExternalConfigSupplier provider = providersByName.get(providerName);
            if (provider == null)
                throw new IllegalArgumentException("No provider found with name '" + providerName + "'");
            return provider.get(key);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateFromBrooklynProperties(ManagementContext mgmt) {
        // form is:
        //     brooklyn.external.<name> : fully.qualified.ClassName
        //     brooklyn.external.<name>.<key> : <value>
        //     brooklyn.external.<name>.<key> : <value>
        //     brooklyn.external.<name>.<key> : <value>

        String EXTERNAL_PROVIDER_PREFIX = "brooklyn.external.";
        Map<String, Object> externalProviderProperties = mgmt.getConfig().submap(ConfigPredicates.startingWith(EXTERNAL_PROVIDER_PREFIX)).asMapWithStringKeys();
        ClassLoader classloader = mgmt.getCatalogClassLoader();
        List<Exception> exceptions = new LinkedList<Exception>();

        for (String key : externalProviderProperties.keySet()) {
            String strippedKey = key.substring(EXTERNAL_PROVIDER_PREFIX.length());
            if (strippedKey.contains("."))
                continue;

            String name = strippedKey;
            String providerClassname = (String) externalProviderProperties.get(key);
            Map<String, Object> config = ConfigUtils.filterForPrefixAndStrip(externalProviderProperties, key + ".");

            try {
                Optional<ExternalConfigSupplier> configSupplier = Reflections.invokeConstructorWithArgs(classloader, providerClassname, mgmt, name, config);
                if (!configSupplier.isPresent()) {
                    configSupplier = Reflections.invokeConstructorWithArgs(classloader, providerClassname, mgmt, name);
                }
                if (!configSupplier.isPresent()) {
                    throw new IllegalStateException("No matching constructor found in "+providerClassname);
                }
                
                addProvider(name, configSupplier.get());

            } catch (Exception e) {
                LOG.error("Failed to instantiate external config supplier named '" + name + "': " + e, e);
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty())
            Exceptions.propagate(exceptions);
    }

}
