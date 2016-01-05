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
package org.apache.brooklyn.core.mgmt.ha;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynVersion;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.core.server.BrooklynServerPaths;
import org.apache.brooklyn.rt.felix.EmbeddedFelixFramework;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.osgi.Osgis;
import org.apache.brooklyn.util.core.osgi.Osgis.BundleFinder;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.os.Os.DeletionResult;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class OsgiManager {

    private static final Logger log = LoggerFactory.getLogger(OsgiManager.class);
    
    public static final ConfigKey<Boolean> USE_OSGI = BrooklynServerConfig.USE_OSGI;
    
    /* see Osgis for info on starting framework etc */
    
    protected ManagementContext mgmt;
    protected Framework framework;
    protected File osgiCacheDir;

    public OsgiManager(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    public void start() {
        try {
            osgiCacheDir = BrooklynServerPaths.getOsgiCacheDirCleanedIfNeeded(mgmt);
            
            // any extra OSGi startup args could go here
            framework = Osgis.getFramework(osgiCacheDir.getAbsolutePath(), false);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public void stop() {
        Osgis.ungetFramework(framework);
        if (BrooklynServerPaths.isOsgiCacheForCleaning(mgmt, osgiCacheDir)) {
            // See exception reported in https://issues.apache.org/jira/browse/BROOKLYN-72
            // We almost always fail to delete he OSGi temp directory due to a concurrent modification.
            // Therefore keep trying.
            final AtomicReference<DeletionResult> deletionResult = new AtomicReference<DeletionResult>();
            Repeater.create("Delete OSGi cache dir")
                    .until(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            deletionResult.set(Os.deleteRecursively(osgiCacheDir));
                            return deletionResult.get().wasSuccessful();
                        }})
                    .limitTimeTo(Duration.ONE_SECOND)
                    .backoffTo(Duration.millis(50))
                    .run();
            if (deletionResult.get().getThrowable()!=null) {
                log.debug("Unable to delete "+osgiCacheDir+" (possibly being modified concurrently?): "+deletionResult.get().getThrowable());
            }
        }
        osgiCacheDir = null;
        framework = null;
    }

    public synchronized void registerBundle(CatalogBundle bundle) {
        try {
            if (checkBundleInstalledThrowIfInconsistent(bundle)) {
                return;
            }

            Bundle b = Osgis.install(framework, bundle.getUrl());

            checkCorrectlyInstalled(bundle, b);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Bundle from "+bundle.getUrl()+" failed to install: " + e.getMessage(), e);
        }
    }

    private void checkCorrectlyInstalled(CatalogBundle bundle, Bundle b) {
        String nv = b.getSymbolicName()+":"+b.getVersion().toString();

        if (!isBundleNameEqualOrAbsent(bundle, b)) {
            throw new IllegalStateException("Bundle already installed as "+nv+" but user explicitly requested "+bundle);
        }

        List<Bundle> matches = Osgis.bundleFinder(framework)
                .symbolicName(b.getSymbolicName())
                .version(b.getVersion().toString())
                .findAll();
        if (matches.isEmpty()) {
            log.error("OSGi could not find bundle "+nv+" in search after installing it from "+bundle.getUrl());
        } else if (matches.size()==1) {
            log.debug("Bundle from "+bundle.getUrl()+" successfully installed as " + nv + " ("+b+")");
        } else {
            log.warn("OSGi has multiple bundles matching "+nv+", when just installed from "+bundle.getUrl()+": "+matches+"; "
                + "brooklyn will prefer the URL-based bundle for top-level references but any dependencies or "
                + "import-packages will be at the mercy of OSGi. "
                + "It is recommended to use distinct versions for different bundles, and the same URL for the same bundles.");
        }
    }

    private boolean checkBundleInstalledThrowIfInconsistent(CatalogBundle bundle) {
        String bundleUrl = bundle.getUrl();
        if (bundleUrl != null) {
            Maybe<Bundle> installedBundle = Osgis.bundleFinder(framework).requiringFromUrl(bundleUrl).find();
            if (installedBundle.isPresent()) {
                Bundle b = installedBundle.get();
                String nv = b.getSymbolicName()+":"+b.getVersion().toString();
                if (!isBundleNameEqualOrAbsent(bundle, b)) {
                    throw new IllegalStateException("User requested bundle " + bundle + " but already installed as "+nv);
                } else {
                    log.trace("Bundle from "+bundleUrl+" already installed as "+nv+"; not re-registering");
                }
                return true;
            }
        } else {
            Maybe<Bundle> installedBundle = Osgis.bundleFinder(framework).symbolicName(bundle.getSymbolicName()).version(bundle.getVersion()).find();
            if (installedBundle.isPresent()) {
                log.trace("Bundle "+bundle+" installed from "+installedBundle.get().getLocation());
            } else {
                throw new IllegalStateException("Bundle "+bundle+" not previously registered, but URL is empty.");
            }
            return true;
        }
        return false;
    }

    public static boolean isBundleNameEqualOrAbsent(CatalogBundle bundle, Bundle b) {
        return !bundle.isNameResolved() ||
                (bundle.getSymbolicName().equals(b.getSymbolicName()) &&
                bundle.getVersion().equals(b.getVersion().toString()));
    }

    public <T> Maybe<Class<T>> tryResolveClass(String type, OsgiBundleWithUrl... osgiBundles) {
        return tryResolveClass(type, Arrays.asList(osgiBundles));
    }
    public <T> Maybe<Class<T>> tryResolveClass(String type, Iterable<? extends OsgiBundleWithUrl> osgiBundles) {
        Map<OsgiBundleWithUrl,Throwable> bundleProblems = MutableMap.of();
        Set<String> extraMessages = MutableSet.of();
        for (OsgiBundleWithUrl osgiBundle: osgiBundles) {
            try {
                Maybe<Bundle> bundle = findBundle(osgiBundle);
                if (bundle.isPresent()) {
                    Bundle b = bundle.get();
                    Class<T> clazz;
                    //Extension bundles don't support loadClass.
                    //Instead load from the app classpath.
                    if (EmbeddedFelixFramework.isExtensionBundle(b)) {
                        @SuppressWarnings("unchecked")
                        Class<T> c = (Class<T>)Class.forName(type);
                        clazz = c;
                    } else {
                        @SuppressWarnings("unchecked")
                        Class<T> c = (Class<T>)b.loadClass(type);
                        clazz = c;
                    }
                    return Maybe.of(clazz);
                } else {
                    bundleProblems.put(osgiBundle, ((Maybe.Absent<?>)bundle).getException());
                }
                
            } catch (Exception e) {
                // should come from classloading now; name formatting or missing bundle errors will be caught above 
                Exceptions.propagateIfFatal(e);
                bundleProblems.put(osgiBundle, e);

                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage().contains("Unresolved constraint in bundle")) {
                    if (BrooklynVersion.INSTANCE.getVersionFromOsgiManifest()==null) {
                        extraMessages.add("No brooklyn-core OSGi manifest available. OSGi will not work.");
                    }
                    if (BrooklynVersion.isDevelopmentEnvironment()) {
                        extraMessages.add("Your development environment may not have created necessary files. Doing a maven build then retrying may fix the issue.");
                    }
                    if (!extraMessages.isEmpty()) log.warn(Strings.join(extraMessages, " "));
                    log.warn("Unresolved constraint resolving OSGi bundle "+osgiBundle+" to load "+type+": "+cause.getMessage());
                    if (log.isDebugEnabled()) log.debug("Trace for OSGi resolution failure", e);
                }
            }
        }
        if (bundleProblems.size()==1) {
            Throwable error = Iterables.getOnlyElement(bundleProblems.values());
            if (error instanceof ClassNotFoundException && error.getCause()!=null && error.getCause().getMessage()!=null) {
                error = Exceptions.collapseIncludingAllCausalMessages(error);
            }
            return Maybe.absent("Unable to resolve class "+type+" in "+Iterables.getOnlyElement(bundleProblems.keySet())
                + (extraMessages.isEmpty() ? "" : " ("+Strings.join(extraMessages, " ")+")"), error);
        } else {
            return Maybe.absent(Exceptions.create("Unable to resolve class "+type+": "+bundleProblems
                + (extraMessages.isEmpty() ? "" : " ("+Strings.join(extraMessages, " ")+")"), bundleProblems.values()));
        }
    }

    public Maybe<Bundle> findBundle(OsgiBundleWithUrl catalogBundle) {
        //Either fail at install time when the user supplied name:version is different
        //from the one reported from the bundle
        //or
        //Ignore user supplied name:version when URL is supplied to be able to find the
        //bundle even if it's with a different version.
        //
        //For now we just log a warning if there's a version discrepancy at install time,
        //so prefer URL if supplied.
        BundleFinder bundleFinder = Osgis.bundleFinder(framework);
        if (catalogBundle.getUrl() != null) {
            bundleFinder.requiringFromUrl(catalogBundle.getUrl());
        } else {
            bundleFinder.symbolicName(catalogBundle.getSymbolicName()).version(catalogBundle.getVersion());
        }
        return bundleFinder.find();
    }

    /**
     * Iterates through catalogBundles until one contains a resource with the given name.
     */
    public URL getResource(String name, Iterable<? extends OsgiBundleWithUrl> osgiBundles) {
        for (OsgiBundleWithUrl osgiBundle: osgiBundles) {
            try {
                Maybe<Bundle> bundle = findBundle(osgiBundle);
                if (bundle.isPresent()) {
                    URL result = bundle.get().getResource(name);
                    if (result!=null) return result;
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
            }
        }
        return null;
    }

    /**
     * @return URL's to all resources matching the given name (using {@link Bundle#getResources(String)} in the referenced osgi bundles.
     */
    public Iterable<URL> getResources(String name, Iterable<? extends OsgiBundleWithUrl> osgiBundles) {
        Set<URL> resources = Sets.newLinkedHashSet();
        for (OsgiBundleWithUrl catalogBundle : osgiBundles) {
            try {
                Maybe<Bundle> bundle = findBundle(catalogBundle);
                if (bundle.isPresent()) {
                    Enumeration<URL> result = bundle.get().getResources(name);
                    resources.addAll(Collections.list(result));
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
            }
        }
        return resources;
    }

    public Framework getFramework() {
        return framework;
    }
    
}
