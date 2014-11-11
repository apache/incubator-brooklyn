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
package brooklyn.config;

import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;
import io.brooklyn.camp.CampPlatform;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogLoadMode;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.management.ManagementContext;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;

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
        + "defaults to the same location spec as regular persisted state, failing back to local file system");
    
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
        "The URL of a catalog.xml descriptor; absent for default (~/.brooklyn/catalog.xml), " +
        "or empty for no URL (use default scanner)",
        new File(Os.fromHome(".brooklyn/catalog.xml")).toURI().toString());

    public static final ConfigKey<CatalogLoadMode> CATALOG_LOAD_MODE = ConfigKeys.newConfigKey(CatalogLoadMode.class,
            "brooklyn.catalog.mode",
            "The mode the management context should use to load the catalog when first starting",
            CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL);

    public static final ConfigKey<Boolean> USE_OSGI = ConfigKeys.newBooleanConfigKey("brooklyn.osgi.enabled",
        "Whether OSGi is enabled, defaulting to true", true);

    public static final ConfigKey<CampPlatform> CAMP_PLATFORM = ConfigKeys.newConfigKey(CampPlatform.class, "brooklyn.camp.platform",
        "Config set at brooklyn management platform to find the CampPlatform instance (bi-directional)");

    public static final AttributeSensor<ManagementContext.PropertiesReloadListener> PROPERTIES_RELOAD_LISTENER = Sensors.newSensor(
            ManagementContext.PropertiesReloadListener.class, "brooklyn.management.propertiesReloadListenet", "Properties reload listener");

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
     * @return the CAMP platform associated with a management context, if there is one.
     */
    public static Maybe<CampPlatform> getCampPlatform(ManagementContext mgmt) {
        CampPlatform result = mgmt.getConfig().getConfig(BrooklynServerConfig.CAMP_PLATFORM);
        if (result!=null) return Maybe.of(result);
        return Maybe.absent("No CAMP Platform is registered with this Brooklyn management context.");
    }

    /**
     * @return {@link ManagementContext#getManagementNodeUri()}, located in this utility class for convenience.
     */
    public static Maybe<URI> getBrooklynWebUri(ManagementContext mgmt) {
        return mgmt.getManagementNodeUri();
    }
    
}
