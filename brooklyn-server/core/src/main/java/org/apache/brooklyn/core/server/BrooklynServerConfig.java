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
package org.apache.brooklyn.core.server;

import static org.apache.brooklyn.core.config.ConfigKeys.newStringConfigKey;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.StringConfigMap;
import org.apache.brooklyn.core.catalog.internal.CatalogInitialization;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.os.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Config keys for the brooklyn server */
public class BrooklynServerConfig {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BrooklynServerConfig.class);

    /**
     * Provided for setting; consumers should use {@link #getMgmtBaseDir(ManagementContext)}
     */
    public static final ConfigKey<String> MGMT_BASE_DIR = newStringConfigKey(
            "brooklyn.base.dir", "Directory for reading and writing all brooklyn server data", 
            Os.fromHome(".brooklyn"));
    
    @Deprecated /** @deprecated since 0.7.0 use BrooklynServerConfig routines */
    // copied here so we don't have back-ref to BrooklynConfigKeys
    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringConfigKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data");

    /**
     * Provided for setting; consumers should query the management context persistence subsystem
     * for the actual target, or use {@link BrooklynServerPaths#newMainPersistencePathResolver(ManagementContext)}
     * if trying to resolve the value
     */
    public static final ConfigKey<String> PERSISTENCE_DIR = newStringConfigKey(
        "brooklyn.persistence.dir", 
        "Directory or container name for writing persisted state");

    public static final ConfigKey<String> PERSISTENCE_LOCATION_SPEC = newStringConfigKey(
        "brooklyn.persistence.location.spec", 
        "Optional location spec string for an object store (e.g. jclouds:swift:URL) where persisted state should be kept; "
        + "if blank or not supplied, the file system is used"); 

    public static final ConfigKey<String> PERSISTENCE_BACKUPS_DIR = newStringConfigKey(
        "brooklyn.persistence.backups.dir", 
        "Directory or container name for writing backups of persisted state; "
        + "defaults to 'backups' inside the default persistence directory");
    
    public static final ConfigKey<String> PERSISTENCE_BACKUPS_LOCATION_SPEC = newStringConfigKey(
        "brooklyn.persistence.backups.location.spec", 
        "Location spec string for an object store (e.g. jclouds:swift:URL) where backups of persisted state should be kept; "
        + "defaults to the local file system");
    
    public static final ConfigKey<Boolean> PERSISTENCE_BACKUPS_REQUIRED_ON_PROMOTION =
        ConfigKeys.newBooleanConfigKey("brooklyn.persistence.backups.required.promotion",
            "Whether a backup should be made of the persisted state from the persistence location to the backup location on node promotion, "
            + "before any writes from this node", true);
    
    public static final ConfigKey<Boolean> PERSISTENCE_BACKUPS_REQUIRED_ON_DEMOTION =
        ConfigKeys.newBooleanConfigKey("brooklyn.persistence.backups.required.promotion",
            "Whether a backup of in-memory state should be made to the backup persistence location on node demotion, "
            + "in case other nodes might write conflicting state", true);

    /** @deprecated since 0.7.0, use {@link #PERSISTENCE_BACKUPS_ON_PROMOTION} and {@link #PERSISTENCE_BACKUPS_ON_DEMOTION},
     * which allow using a different target location and are supported on more environments (and now default to true) */
    @Deprecated
    public static final ConfigKey<Boolean> PERSISTENCE_BACKUPS_REQUIRED =
        ConfigKeys.newBooleanConfigKey("brooklyn.persistence.backups.required",
            "Whether a backup should always be made of the persistence directory; "
            + "if true, it will fail if this operation is not permitted (e.g. jclouds-based cloud object stores); "
            + "if false, the persistence store will be overwritten with changes (but files not removed if they are unreadable); "
            + "if null or not set, the legacy beahviour of creating backups where possible (e.g. file system) is currently used; "
            + "this key is DEPRECATED in favor of promotion and demotion specific flags now defaulting to true");

    public static final ConfigKey<String> BROOKLYN_CATALOG_URL = ConfigKeys.newStringConfigKey("brooklyn.catalog.url",
        "The URL of a custom catalog.bom or catalog.xml descriptor to load");

    /** @deprecated since 0.7.0 replaced by {@link CatalogInitialization}; also note, default removed 
     * (it was overridden anyway, and in almost all cases the new behaviour is still the default behaviour) */
    @Deprecated
    public static final ConfigKey<org.apache.brooklyn.core.catalog.CatalogLoadMode> CATALOG_LOAD_MODE = ConfigKeys.newConfigKey(org.apache.brooklyn.core.catalog.CatalogLoadMode.class,
            "brooklyn.catalog.mode",
            "The mode the management context should use to load the catalog when first starting");

    /** string used in places where the management node ID is needed to resolve a path */
    public static final String MANAGEMENT_NODE_ID_PROPERTY = "brooklyn.mgmt.node.id";
    
    public static final ConfigKey<Boolean> USE_OSGI = ConfigKeys.newBooleanConfigKey("brooklyn.osgi.enabled",
        "Whether OSGi is enabled, defaulting to true", true);
    public static final ConfigKey<String> OSGI_CACHE_DIR = ConfigKeys.newStringConfigKey("brooklyn.osgi.cache.dir",
        "Directory to use for OSGi cache, potentially including Freemarker template variables "
        + "${"+MGMT_BASE_DIR.getName()+"} (which is the default for relative paths), "
        + "${"+Os.TmpDirFinder.BROOKLYN_OS_TMPDIR_PROPERTY+"} if it should be in the tmp dir space,  "
        + "and ${"+MANAGEMENT_NODE_ID_PROPERTY+"} to include the management node ID (recommended if running multiple OSGi paths)",
        "osgi/cache/${"+MANAGEMENT_NODE_ID_PROPERTY+"}/");
    public static final ConfigKey<Boolean> OSGI_CACHE_CLEAN = ConfigKeys.newBooleanConfigKey("brooklyn.osgi.cache.clean",
        "Whether to delete the OSGi directory before and after use; if unset, it will delete if the node ID forms part of the cache dir path (which by default it does) to avoid file leaks");

    /** @see BrooklynServerPaths#getMgmtBaseDir(ManagementContext) */
    public static String getMgmtBaseDir(ManagementContext mgmt) {
        return BrooklynServerPaths.getMgmtBaseDir(mgmt);
    }
    /** @see BrooklynServerPaths#getMgmtBaseDir(ManagementContext) */
    public static String getMgmtBaseDir(StringConfigMap brooklynProperties) {
        return BrooklynServerPaths.getMgmtBaseDir(brooklynProperties);
    }
    /** @see BrooklynServerPaths#getMgmtBaseDir(ManagementContext) */
    public static String getMgmtBaseDir(Map<String,?> brooklynProperties) {
        return BrooklynServerPaths.getMgmtBaseDir(brooklynProperties);
    }
    
    /** @deprecated since 0.7.0 use {@link BrooklynServerPaths#newMainPersistencePathResolver(ManagementContext)} */
    public static String getPersistenceDir(ManagementContext mgmt) {
        return getPersistenceDir(mgmt.getConfig());
    }
    /** @deprecated since 0.7.0 use {@link BrooklynServerPaths#newMainPersistencePathResolver(ManagementContext)} */ 
    public static String getPersistenceDir(StringConfigMap brooklynProperties) {
        return resolvePersistencePath(null, brooklynProperties, null);
    }
    
    /**
     * @param optionalSuppliedValue
     *     An optional value which has been supplied explicitly
     * @param brooklynProperties
     *     The properties map where the persistence path should be looked up if not supplied,
     *     along with finding the brooklyn.base.dir if needed (using file system persistence
     *     with a relative path)
     * @param optionalObjectStoreLocationSpec
     *     If a location spec is supplied, this will return a container name suitable for use
     *     with the given object store based on brooklyn.persistence.dir; if null this method
     *     will return a full file system path, relative to the brooklyn.base.dir if the
     *     configured brooklyn.persistence.dir is not absolute
     * @return The container name or full path for where persist state should be kept
     * @deprecated since 0.7.0 use {@link BrooklynServerPaths#newMainPersistencePathResolver(ManagementContext)} */
    public static String resolvePersistencePath(String optionalSuppliedValue, StringConfigMap brooklynProperties, String optionalObjectStoreLocationSpec) {
        return BrooklynServerPaths.newMainPersistencePathResolver(brooklynProperties).location(optionalObjectStoreLocationSpec).dir(optionalSuppliedValue).resolve();
    }
    
    
    /** @deprecated since 0.7.0 use {@link BrooklynServerPaths#getBrooklynWebTmpDir(ManagementContext)} */
    public static File getBrooklynWebTmpDir(ManagementContext mgmt) {
        return BrooklynServerPaths.getBrooklynWebTmpDir(mgmt);
    }

    /**
     * @return {@link ManagementContext#getManagementNodeUri()}, located in this utility class for convenience.
     */
    public static Maybe<URI> getBrooklynWebUri(ManagementContext mgmt) {
        return mgmt.getManagementNodeUri();
    }
    
}
