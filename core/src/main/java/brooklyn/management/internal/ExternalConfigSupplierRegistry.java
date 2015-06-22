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

import com.google.common.annotations.Beta;

import brooklyn.config.external.ExternalConfigSupplier;


/**
 * Permits a number of {@link ExternalConfigSupplier} instances to be registered, each with a unique name, for future
 * (deferred) lookup of configuration values.
 *
 * @since 0.8.0
 */
@Beta
public interface ExternalConfigSupplierRegistry {

    void addProvider(String name, ExternalConfigSupplier provider);
    void removeProvider(String name);

    /**
     * Searches the named {@link ExternalConfigSupplier} for the config value associated with the specified key.
     * Quietly returns <code>null</code> if no config exists for the specified key.
     * Throws {@link IllegalArgumentException} if no {@link ExternalConfigSupplier} exists for the passed name.
     */
    public String getConfig(String providerName, String key);

}
