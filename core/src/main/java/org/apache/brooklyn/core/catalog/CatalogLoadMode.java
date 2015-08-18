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
package org.apache.brooklyn.core.catalog;

import org.apache.brooklyn.core.catalog.internal.CatalogInitialization;
import org.apache.brooklyn.core.mgmt.rebind.persister.PersistMode;
import org.slf4j.LoggerFactory;

/** @deprecated since 0.7.0 replaced by {@link CatalogInitialization} */
@Deprecated
public enum CatalogLoadMode {
    /**
     * The server will load its initial catalog from the URL configured in
     * {@link brooklyn.config.BrooklynServerConfig#BROOKLYN_CATALOG_URL} and will
     * disregard existing persisted state.
     */
    LOAD_BROOKLYN_CATALOG_URL,

    /**
     * The server will load its initial catalog from previously persisted state,
     * and will behave as {@link #LOAD_BROOKLYN_CATALOG_URL} if no state exists.
     */
    LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE,

    /**
     * The server will load its initial catalog from previously persisted state.
     * The catalog will be empty if no previous state exists.
     */
    LOAD_PERSISTED_STATE;

    /**
     * @return A catalog load mode suitable for the given persistence mode:
     * <ul>
     * <li>disabled: {@link #LOAD_BROOKLYN_CATALOG_URL}</li>
     * <li>rebind: {@link #LOAD_PERSISTED_STATE}</li>
     * <li>auto or clean: {@link #LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE}</li>
     * </ul>
     */
    public static CatalogLoadMode forPersistMode(PersistMode m) {
        // Clean case relies on the persistence directory being cleaned and rebind manager
        // believing the store to be empty.
        switch (m) {
        case DISABLED:
            return LOAD_BROOKLYN_CATALOG_URL;
        case AUTO:
        case CLEAN:
            return LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE;
        case REBIND:
            return LOAD_PERSISTED_STATE;
        default:
            LoggerFactory.getLogger(CatalogLoadMode.class)
                    .warn("Unhandled persistence mode {}. Catalog defaulting to {}", m.name(), LOAD_BROOKLYN_CATALOG_URL.name());
            return LOAD_BROOKLYN_CATALOG_URL;
        }
    }
}
