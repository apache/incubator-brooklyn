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

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogItemLibraries;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.BrooklynClassLoadingContextSequential;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.classloading.OsgiBrooklynClassLoadingContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.Time;

public class CatalogUtils {
    private static final Logger log = LoggerFactory.getLogger(CatalogUtils.class);

    public static BrooklynClassLoadingContext newClassLoadingContext(ManagementContext mgmt, CatalogItem<?, ?> item) {
        CatalogItemLibraries libraries = item.getLibraries();
        // TODO getLibraries() should never be null but sometimes it is still
        // e.g. run CatalogResourceTest without the above check
        if (libraries == null) {
            log.debug("CatalogItemDtoAbstract.getLibraries() is null.", new Exception("Trace for null CatalogItemDtoAbstract.getLibraries()"));
        }
        return newClassLoadingContext(mgmt, item.getId(), libraries, mgmt.getCatalog().getRootClassLoader());
    }

    public static BrooklynClassLoadingContext newClassLoadingContext(@Nullable ManagementContext mgmt, String catalogItemId, CatalogItemLibraries libraries, ClassLoader classLoader) {
        BrooklynClassLoadingContextSequential result = new BrooklynClassLoadingContextSequential(mgmt);

        if (libraries!=null) {
            List<String> bundles = libraries.getBundles();
            if (bundles!=null && !bundles.isEmpty()) {
                result.add(new OsgiBrooklynClassLoadingContext(mgmt, catalogItemId, bundles));
            }
        }

        BrooklynClassLoadingContext loader = BrooklynLoaderTracker.getLoader();
        if (loader != null) {
            result.add(loader);
        }

        result.addSecondary(JavaBrooklynClassLoadingContext.create(mgmt, classLoader));
        return result;
    }

    /**
     * Registers all bundles with the management context's OSGi framework.
     */
    public static void installLibraries(ManagementContext managementContext, @Nullable CatalogItemLibraries libraries) {
        if (libraries == null) return;

        ManagementContextInternal mgmt = (ManagementContextInternal) managementContext;
        List<String> bundles = libraries.getBundles();
        if (!bundles.isEmpty()) {
            Maybe<OsgiManager> osgi = mgmt.getOsgiManager();
            if (osgi.isAbsent()) {
                throw new IllegalStateException("Unable to load bundles "+bundles+" because OSGi is not running.");
            }
            if (log.isDebugEnabled()) log.debug("Loading bundles in {}: {}", 
                    new Object[] {managementContext, Joiner.on(", ").join(bundles)});
            Stopwatch timer = Stopwatch.createStarted();
            for (String bundleUrl : bundles) {
                osgi.get().registerBundle(bundleUrl);
            }
            if (log.isDebugEnabled()) log.debug("Registered {} bundles in {}",
                new Object[]{bundles.size(), Time.makeTimeStringRounded(timer)});
        }
    }

    public static String getContextCatalogItemIdFromLoader(BrooklynClassLoadingContext loader) {
        if (loader instanceof OsgiBrooklynClassLoadingContext) {
            return ((OsgiBrooklynClassLoadingContext)loader).getContextCatalogId();
        } else {
            return null;
        }
    }

}
