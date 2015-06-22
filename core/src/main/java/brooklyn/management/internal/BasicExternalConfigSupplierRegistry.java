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
package brooklyn.management.internal;

import java.util.Map;

import brooklyn.config.external.ExternalConfigSupplier;

import com.google.common.collect.Maps;

/**
 * Simple registry implementation.
 *
 * Permits a number of {@link ExternalConfigSupplier} instances to be registered, each with a unique name, for future
 * (deferred) lookup of configuration values.
 */
public class BasicExternalConfigSupplierRegistry implements ExternalConfigSupplierRegistry {

    private final Map<String, ExternalConfigSupplier> providersByName = Maps.newLinkedHashMap();
    private final Object providersMapMutex = new Object();

    public BasicExternalConfigSupplierRegistry() {
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

}
