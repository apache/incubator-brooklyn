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
package brooklyn.catalog.internal;

import java.util.Collection;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.basic.BrooklynObjectInternal;

import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;

import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.config.BrooklynLogging;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.RebindManagerImpl.RebindTracker;
import brooklyn.management.classloading.BrooklynClassLoadingContextSequential;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.classloading.OsgiBrooklynClassLoadingContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;

public class CatalogUtils {
    private static final Logger log = LoggerFactory.getLogger(CatalogUtils.class);

    public static final char VERSION_DELIMITER = ':';

    public static BrooklynClassLoadingContext newClassLoadingContext(ManagementContext mgmt, CatalogItem<?, ?> item) {
        // TODO getLibraries() should never be null but sometimes it is still
        // e.g. run CatalogResourceTest without the above check
        if (item.getLibraries() == null) {
            log.debug("CatalogItemDtoAbstract.getLibraries() is null.", new Exception("Trace for null CatalogItemDtoAbstract.getLibraries()"));
        }
        return newClassLoadingContext(mgmt, item.getId(), item.getLibraries());
    }
    
    public static BrooklynClassLoadingContext getClassLoadingContext(Entity entity) {
        ManagementContext mgmt = ((EntityInternal)entity).getManagementContext();
        String catId = entity.getCatalogItemId();
        if (Strings.isBlank(catId)) return JavaBrooklynClassLoadingContext.create(mgmt);
        CatalogItem<?, ?> cat = getCatalogItemOptionalVersion(mgmt, catId);
        if (cat==null) {
            log.warn("Cannot load "+catId+" to get classloader for "+entity+"; will try with standard loader, but might fail subsequently");
            return JavaBrooklynClassLoadingContext.create(mgmt);
        }
        return newClassLoadingContext(mgmt, cat);
    }

    public static BrooklynClassLoadingContext newClassLoadingContext(@Nullable ManagementContext mgmt, String catalogItemId, Collection<CatalogBundle> libraries) {
        BrooklynClassLoadingContextSequential result = new BrooklynClassLoadingContextSequential(mgmt);

        if (libraries!=null && !libraries.isEmpty()) {
            result.add(new OsgiBrooklynClassLoadingContext(mgmt, catalogItemId, libraries));
        }

        BrooklynClassLoadingContext loader = BrooklynLoaderTracker.getLoader();
        if (loader != null) {
            result.add(loader);
        }

        result.addSecondary(JavaBrooklynClassLoadingContext.create(mgmt));
        return result;
    }

    /**
     * @deprecated since 0.7.0 only for legacy catalog items which provide a non-osgi loader; see {@link #newDefault(ManagementContext)}
     */ @Deprecated
    public static BrooklynClassLoadingContext newClassLoadingContext(@Nullable ManagementContext mgmt, String catalogItemId, Collection<CatalogBundle> libraries, ClassLoader customClassLoader) {
        BrooklynClassLoadingContextSequential result = new BrooklynClassLoadingContextSequential(mgmt);

        if (libraries!=null && !libraries.isEmpty()) {
            result.add(new OsgiBrooklynClassLoadingContext(mgmt, catalogItemId, libraries));
        }

        BrooklynClassLoadingContext loader = BrooklynLoaderTracker.getLoader();
        if (loader != null) {
            result.add(loader);
        }

        result.addSecondary(JavaBrooklynClassLoadingContext.create(mgmt, customClassLoader));
        return result;
    }

    /**
     * Registers all bundles with the management context's OSGi framework.
     */
    public static void installLibraries(ManagementContext managementContext, @Nullable Collection<CatalogBundle> libraries) {
        if (libraries == null) return;

        ManagementContextInternal mgmt = (ManagementContextInternal) managementContext;
        if (!libraries.isEmpty()) {
            Maybe<OsgiManager> osgi = mgmt.getOsgiManager();
            if (osgi.isAbsent()) {
                throw new IllegalStateException("Unable to load bundles "+libraries+" because OSGi is not running.");
            }
            if (log.isDebugEnabled()) 
                logDebugOrTraceIfRebinding(log, 
                    "Loading bundles in {}: {}", 
                    new Object[] {managementContext, Joiner.on(", ").join(libraries)});
            Stopwatch timer = Stopwatch.createStarted();
            for (CatalogBundle bundleUrl : libraries) {
                osgi.get().registerBundle(bundleUrl);
            }
            if (log.isDebugEnabled()) 
                logDebugOrTraceIfRebinding(log, 
                    "Registered {} bundles in {}",
                    new Object[]{libraries.size(), Time.makeTimeStringRounded(timer)});
        }
    }

    /** Scans the given {@link BrooklynClassLoadingContext} to detect what catalog item id is in effect. */
    public static String getCatalogItemIdFromLoader(BrooklynClassLoadingContext loader) {
        if (loader instanceof OsgiBrooklynClassLoadingContext) {
            return ((OsgiBrooklynClassLoadingContext)loader).getCatalogItemId();
        } else {
            return null;
        }
    }

    public static void setCatalogItemIdOnAddition(Entity entity, BrooklynObject itemBeingAdded) {
        if (entity.getCatalogItemId()!=null) {
            if (itemBeingAdded.getCatalogItemId()==null) {
                if (log.isDebugEnabled())
                    BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(entity),
                        "Catalog item addition: "+entity+" from "+entity.getCatalogItemId()+" applying its catalog item ID to "+itemBeingAdded);
                ((BrooklynObjectInternal)itemBeingAdded).setCatalogItemId(entity.getCatalogItemId());
            } else {
                if (!itemBeingAdded.getCatalogItemId().equals(entity.getCatalogItemId())) {
                    // not a problem, but something to watch out for
                    log.debug("Cross-catalog item detected: "+entity+" from "+entity.getCatalogItemId()+" has "+itemBeingAdded+" from "+itemBeingAdded.getCatalogItemId());
                }
            }
        } else if (itemBeingAdded.getCatalogItemId()!=null) {
            if (log.isDebugEnabled())
                BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(entity),
                    "Catalog item addition: "+entity+" without catalog item ID has "+itemBeingAdded+" from "+itemBeingAdded.getCatalogItemId());
        }
    }

    @Beta
    public static void logDebugOrTraceIfRebinding(Logger log, String message, Object ...args) {
        if (RebindTracker.isRebinding())
            log.trace(message, args);
        else
            log.debug(message, args);
    }

    public static boolean looksLikeVersionedId(String versionedId) {
        if (versionedId==null) return false;
        int fi = versionedId.indexOf(VERSION_DELIMITER);
        if (fi<0) return false;
        int li = versionedId.lastIndexOf(VERSION_DELIMITER);
        if (li!=fi) {
            // if multiple colons, we say it isn't a versioned reference; the prefix in that case must understand any embedded versioning scheme
            // this fixes the case of:  http://localhost:8080
            return false;
        }
        String candidateVersion = versionedId.substring(li+1);
        if (!candidateVersion.matches("[0-9]+(|(\\.|_).*)")) {
            // version must start with a number, followed if by anything with full stop or underscore before any other characters
            // e.g.  foo:1  or foo:1.1  or foo:1_SNAPSHOT all supported, but not e.g. foo:bar (or chef:cookbook or docker:my/image)
            return false;
        }
        return true;
    }

    public static String getIdFromVersionedId(String versionedId) {
        if (versionedId == null) return null;
        int versionDelimiterPos = versionedId.lastIndexOf(VERSION_DELIMITER);
        if (versionDelimiterPos != -1) {
            return versionedId.substring(0, versionDelimiterPos);
        } else {
            return null;
        }
    }

    public static String getVersionFromVersionedId(String versionedId) {
        if (versionedId == null) return null;
        int versionDelimiterPos = versionedId.lastIndexOf(VERSION_DELIMITER);
        if (versionDelimiterPos != -1) {
            return versionedId.substring(versionDelimiterPos+1);
        } else {
            return null;
        }
    }

    public static String getVersionedId(String id, String version) {
        // TODO null checks
        return id + VERSION_DELIMITER + version;
    }

    //TODO Don't really like this, but it's better to have it here than on the interface to keep the API's 
    //surface minimal. Could instead have the interface methods accept VerionedId object and have the helpers
    //construct it as needed.
    public static CatalogItem<?, ?> getCatalogItemOptionalVersion(ManagementContext mgmt, String versionedId) {
        if (versionedId == null) return null;
        if (looksLikeVersionedId(versionedId)) {
            String id = getIdFromVersionedId(versionedId);
            String version = getVersionFromVersionedId(versionedId);
            return mgmt.getCatalog().getCatalogItem(id, version);
        } else {
            return mgmt.getCatalog().getCatalogItem(versionedId, BrooklynCatalog.DEFAULT_VERSION);
        }
    }

    public static boolean isBestVersion(ManagementContext mgmt, CatalogItem<?,?> item) {
        CatalogItem<?, ?> bestVersion = getCatalogItemOptionalVersion(mgmt, item.getSymbolicName());
        if (bestVersion==null) return false;
        return (bestVersion.getVersion().equals(item.getVersion()));
    }

    public static <T,SpecT> CatalogItem<T, SpecT> getCatalogItemOptionalVersion(ManagementContext mgmt, Class<T> type, String versionedId) {
        if (looksLikeVersionedId(versionedId)) {
            String id = getIdFromVersionedId(versionedId);
            String version = getVersionFromVersionedId(versionedId);
            return mgmt.getCatalog().getCatalogItem(type, id, version);
        } else {
            return mgmt.getCatalog().getCatalogItem(type, versionedId, BrooklynCatalog.DEFAULT_VERSION);
        }
    }

}
