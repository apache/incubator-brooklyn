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

import java.io.File;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;

public class BrooklynServerPaths {

    private static final Logger log = LoggerFactory.getLogger(BrooklynServerPaths.class);
    
    /** Computes the base dir where brooklyn should read and write configuration.
     * Defaults to <code>~/.brooklyn/</code>. 
     * <p>
     * Also see other variants of this method if a {@link ManagementContext} is not yet available. */
    public static String getMgmtBaseDir(ManagementContext mgmt) {
        return getMgmtBaseDir(mgmt.getConfig());
    }
    
    /** @see BrooklynServerPaths#getMgmtBaseDir(ManagementContext) */
    @SuppressWarnings("deprecation")
    public static String getMgmtBaseDir(StringConfigMap brooklynProperties) {
        String base = (String) brooklynProperties.getConfigRaw(BrooklynServerConfig.MGMT_BASE_DIR, true).orNull();
        if (base==null) {
            base = brooklynProperties.getConfig(BrooklynServerConfig.BROOKLYN_DATA_DIR);
            if (base!=null)
                log.warn("Using deprecated "+BrooklynServerConfig.BROOKLYN_DATA_DIR.getName()+": use "+BrooklynServerConfig.MGMT_BASE_DIR.getName()+" instead; value: "+base);
        }
        if (base==null) base = brooklynProperties.getConfig(BrooklynServerConfig.MGMT_BASE_DIR);
        return Os.tidyPath(base)+File.separator;
    }
    /** @see BrooklynServerPaths#getMgmtBaseDir(ManagementContext) */
    @SuppressWarnings("deprecation")
    public static String getMgmtBaseDir(Map<String,?> brooklynProperties) {
        String base = (String) brooklynProperties.get(BrooklynServerConfig.MGMT_BASE_DIR.getName());
        if (base==null) base = (String) brooklynProperties.get(BrooklynServerConfig.BROOKLYN_DATA_DIR.getName());
        if (base==null) base = BrooklynServerConfig.MGMT_BASE_DIR.getDefaultValue();
        return Os.tidyPath(base)+File.separator;
    }
    
    protected static String resolveAgainstBaseDir(StringConfigMap brooklynProperties, String path) {
        if (!Os.isAbsolutish(path)) path = Os.mergePaths(getMgmtBaseDir(brooklynProperties), path);
        return Os.tidyPath(path);
    }

    // ---------- persistence
    
    public static final String DEFAULT_PERSISTENCE_CONTAINER_NAME = "brooklyn-persisted-state";
    /** on file system, the 'data' subdir is used so that there is an obvious place to put backup dirs */ 
    public static final String DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM = Os.mergePaths(DEFAULT_PERSISTENCE_CONTAINER_NAME, "data");

    /** @see PersistencePathResolver */
    public static PersistencePathResolver newMainPersistencePathResolver(StringConfigMap brooklynProperties) {
        return new PersistencePathResolver(brooklynProperties);
    }
    /** @see PersistencePathResolver */
    public static PersistencePathResolver newMainPersistencePathResolver(ManagementContext mgmt) {
        return new PersistencePathResolver(mgmt.getConfig());
    }
    
    /** @see PersistenceBackupPathResolver */
    public static PersistenceBackupPathResolver newBackupPersistencePathResolver(ManagementContext mgmt) {
        return new PersistenceBackupPathResolver(mgmt.getConfig());
    }
    
    /**
     * Utility for computing the path (dir or container name) to use for persistence.
     * <p>
     * Usage is to invoke {@link BrooklynServerPaths#newMainPersistencePathResolver(ManagementContext)}
     * then to set {@link #location(String)} and {@link #dir(String)} as needed, and then to {@link #resolve()}.
     */
    public static class PersistencePathResolver {
        protected final StringConfigMap brooklynProperties;
        protected String locationSpec;
        protected String dirOrContainer;
        private PersistencePathResolver(StringConfigMap brooklynProperties) {
            this.brooklynProperties = brooklynProperties;
        }
        /** 
         * Optional location spec. If supplied, the {@link #resolve()} will return a container name suitable for use
         * with the store, based on the {@link #dir(String)}; 
         * if not supplied, or blank, or localhost this will cause resolution to give a full file system path, 
         * if relative taken with respect to the {@link BrooklynServerPaths#getMgmtBaseDir(ManagementContext)}. 
         * Config is <b>not</b> looked up for resolving a location spec. */
        public PersistencePathResolver location(@Nullable String locationSpec) {
            this.locationSpec = locationSpec;
            return this;
        }
        /** 
         * Optional directory (for localhost/filesystem) or container name. 
         * If null or not supplied, config <b>is</b> looked up (because a value is always needed),
         * followed by defaults for {@link BrooklynServerPaths#DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM} and 
         * {@link BrooklynServerPaths#DEFAULT_PERSISTENCE_CONTAINER_NAME} are used. */
        public PersistencePathResolver dir(@Nullable String dirOrContainer) {
            this.dirOrContainer = dirOrContainer;
            return this;
        }
        
        public String resolve() {
            String path = dirOrContainer;
            if (path==null) path = getDefaultPathFromConfig();
            if (Strings.isBlank(locationSpec) || "localhost".equals(locationSpec)) {
                // file system
                if (Strings.isBlank(path)) path=getDefaultDirForAnyFilesystem();
                return resolveAgainstBaseDir(brooklynProperties, path);
            } else {
                // obj store
                if (path==null) path=getDefaultContainerForAnyNonFilesystem();
                return path;
            }
        }
        
        protected String getDefaultPathFromConfig() {
            return brooklynProperties.getConfig(BrooklynServerConfig.PERSISTENCE_DIR);
        }
        protected String getDefaultDirForAnyFilesystem() {
            return DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM;
        }
        protected String getDefaultContainerForAnyNonFilesystem() {
            return DEFAULT_PERSISTENCE_CONTAINER_NAME;
        }
    }
    
    /**
     * Similar to {@link PersistencePathResolver}, but designed for use for backup subpaths.
     * If the container is not explicitly specified, "backups" is appended to the defaults from {@link PersistencePathResolver}.
     * <p>
     * It also includes conveniences for resolving further subpaths, cf {@link PersistenceBackupPathResolver#resolveWithSubpathFor(ManagementContextInternal, String)}.
     */
    public static class PersistenceBackupPathResolver extends PersistencePathResolver {
        private String nonBackupLocationSpec;
        private PersistenceBackupPathResolver(StringConfigMap brooklynProperties) {
            super(brooklynProperties);
        }
        public PersistenceBackupPathResolver nonBackupLocation(@Nullable String locationSpec) {
            this.nonBackupLocationSpec = locationSpec;
            return this;
        }
        @Override
        public PersistenceBackupPathResolver dir(String dirOrContainer) {
            super.dir(dirOrContainer);
            return this;
        }
        @Override
        public PersistenceBackupPathResolver location(String backupLocationSpec) {
            super.location(backupLocationSpec);
            return this;
        }
        protected boolean isBackupSameLocation() {
            return Objects.equal(locationSpec, nonBackupLocationSpec);
        }
        /** Appends a sub-path to the path returned by {@link #resolve()} */
        public String resolveWithSubpath(String subpath) {
            return Urls.mergePaths(super.resolve(), subpath);
        }
        /** Appends a standard format subpath sub-path to the path returned by {@link #resolve()}.
         * <p>
         * For example, this might write to:
         * <code>~/.brooklyn/brooklyn-persisted-state/backups/2014-11-13-1201-n0deId-promotion-sA1t */
        public String resolveWithSubpathFor(ManagementContext managementContext, String label) {
            return resolveWithSubpath(Time.makeDateSimpleStampString()+"-"+managementContext.getManagementNodeId()+"-"+label+"-"+Identifiers.makeRandomId(4));
        }
        @Override
        protected String getDefaultPathFromConfig() {
            Maybe<Object> result = brooklynProperties.getConfigRaw(BrooklynServerConfig.PERSISTENCE_BACKUPS_DIR, true);
            if (result.isPresent()) return Strings.toString(result.get());
            if (isBackupSameLocation()) {
                return backupContainerFor(brooklynProperties.getConfig(BrooklynServerConfig.PERSISTENCE_DIR));
            }
            return null;
        }
        protected String backupContainerFor(String nonBackupContainer) {
            if (nonBackupContainer==null) return null;
            return Urls.mergePaths(nonBackupContainer, "backups");
        }
        @Override
        protected String getDefaultDirForAnyFilesystem() {
            return backupContainerFor(DEFAULT_PERSISTENCE_CONTAINER_NAME);
        }
        @Override
        protected String getDefaultContainerForAnyNonFilesystem() {
            return backupContainerFor(super.getDefaultContainerForAnyNonFilesystem());
        }
    }
    
    // ------ web
    
    public static File getBrooklynWebTmpDir(ManagementContext mgmt) {
        String brooklynMgmtBaseDir = getMgmtBaseDir(mgmt);
        File webappTempDir = new File(Os.mergePaths(brooklynMgmtBaseDir, "planes", mgmt.getManagementPlaneId(), mgmt.getManagementNodeId(), "jetty"));
        try {
            FileUtils.forceMkdir(webappTempDir);
            Os.deleteOnExitRecursivelyAndEmptyParentsUpTo(webappTempDir, new File(brooklynMgmtBaseDir)); 
            return webappTempDir;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            IllegalStateException e2 = new IllegalStateException("Cannot create working directory "+webappTempDir+" for embedded jetty server: "+e, e);
            log.warn(e2.getMessage()+" (rethrowing)");
            throw e2;
        }
    }

    public static File getOsgiCacheDir(ManagementContext mgmt) {
        StringConfigMap brooklynProperties = mgmt.getConfig();
        String cacheDir = brooklynProperties.getConfig(BrooklynServerConfig.OSGI_CACHE_DIR);
        
        // note dir should be different for each instance if starting multiple instances
        // hence default including management node ID
        
        cacheDir = TemplateProcessor.processTemplateContents(cacheDir, (ManagementContextInternal)mgmt, 
            MutableMap.of(BrooklynServerConfig.MGMT_BASE_DIR.getName(), getMgmtBaseDir(mgmt),
                BrooklynServerConfig.MANAGEMENT_NODE_ID_PROPERTY, mgmt.getManagementNodeId(),
                Os.TmpDirFinder.BROOKLYN_OS_TMPDIR_PROPERTY, Os.tmp()));
        cacheDir = resolveAgainstBaseDir(mgmt.getConfig(), cacheDir);
        
        return new File(cacheDir);
    }

    public static boolean isOsgiCacheForCleaning(ManagementContext mgmt, File cacheDir) {
        StringConfigMap brooklynProperties = mgmt.getConfig();
        Boolean clean = brooklynProperties.getConfig(BrooklynServerConfig.OSGI_CACHE_CLEAN);
        if (clean==null) {
            // as per javadoc on key, clean defaults to iff it is a node-id-specific directory
            clean = cacheDir.getName().contains(mgmt.getManagementNodeId());
        }
        return clean;
    }
    
    public static File getOsgiCacheDirCleanedIfNeeded(ManagementContext mgmt) {
        File cacheDirF = getOsgiCacheDir(mgmt);
        boolean clean = isOsgiCacheForCleaning(mgmt, cacheDirF);
        log.debug("OSGi cache dir computed as "+cacheDirF.getName()+" ("+
            (cacheDirF.exists() ? "already exists" : "does not exist")+", "+
            (clean ? "cleaning now (and on exit)" : "cleaning not requested"));

        if (clean) {
            Os.deleteRecursively(cacheDirF);
            Os.deleteOnExitRecursively(cacheDirF);
        }
        
        return cacheDirF;
    }

}
