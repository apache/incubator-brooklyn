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
package brooklyn.management.ha;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem.CatalogBundle;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.os.Os.DeletionResult;
import brooklyn.util.osgi.Osgis;
import brooklyn.util.osgi.Osgis.BundleFinder;
import brooklyn.util.osgi.Osgis.VersionedName;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;

public class OsgiManager {

    private static final Logger log = LoggerFactory.getLogger(OsgiManager.class);
    
    public static final ConfigKey<Boolean> USE_OSGI = BrooklynServerConfig.USE_OSGI;
    
    /* see Osgis for info on starting framework etc */
    
    protected Framework framework;
    protected File osgiTempDir;

    // we could manage without this map but it is useful to validate what is a user-supplied url
    protected Map<String,VersionedName> urlToBundleIdentifier = MutableMap.of();

    public void start() {
        try {
            // TODO any extra startup args?
            // TODO dir to come from brooklyn properties;
            // note dir must be different for each if starting multiple instances
            osgiTempDir = Os.newTempDir("brooklyn-osgi-cache");
            framework = Osgis.newFrameworkStarted(osgiTempDir.getAbsolutePath(), false, MutableMap.of());
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public void stop() {
        try {
            if (framework!=null) {
                framework.stop();
                framework.waitForStop(0);
            }
        } catch (BundleException e) {
            throw Exceptions.propagate(e);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        DeletionResult deleteRecursively = Os.deleteRecursively(osgiTempDir);
        if (deleteRecursively.getThrowable()!=null) {
            log.debug("Unable to delete "+osgiTempDir+" (possibly already deleted?): "+deleteRecursively.getThrowable());
        }
        osgiTempDir = null;
        framework = null;
    }

    public synchronized void registerBundle(CatalogBundle bundle) {
        try {
            if (checkBundleInstalledThrowIfInconsistent(bundle)) {
                return;
            }

            Bundle b = Osgis.install(framework, bundle.getUrl());

            checkCorrectlyInstalled(bundle, b);

            urlToBundleIdentifier.put(bundle.getUrl(), new VersionedName(b));
        } catch (BundleException e) {
            log.debug("Bundle from "+bundle+" failed to install (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    private void checkCorrectlyInstalled(CatalogBundle bundle, Bundle b) {
        String nv = b.getSymbolicName()+":"+b.getVersion().toString();

        if (bundle.isNamed() &&
                (!bundle.getSymbolicName().equals(b.getSymbolicName()) ||
                !bundle.getVersion().equals(b.getVersion().toString()))) {
            log.warn("Bundle at " + bundle.getUrl() + " installed as " + nv +
                    " but user explicitly requested " + bundle.getSymbolicName() + ":" + bundle.getVersion());
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
            VersionedName nv = urlToBundleIdentifier.get(bundleUrl);
            if (nv!=null) {
                if (bundle.isNamed() && !nv.equals(bundle.getSymbolicName(), bundle.getVersion())) {
                    throw new IllegalStateException("Bundle from "+bundleUrl+" already installed as "+nv+" but user explicitly requested "+bundle);
                }
                Maybe<Bundle> installedBundle = Osgis.bundleFinder(framework).requiringFromUrl(bundleUrl).find();
                if (installedBundle.isPresent()) {
                    if (bundle.isNamed()) {
                        Bundle b = installedBundle.get();
                        if (!nv.equals(b.getSymbolicName(), b.getVersion().toString())) {
                            log.error("Bundle from "+bundleUrl+" already installed as "+nv+" but reports "+b.getSymbolicName()+":"+b.getVersion());
                        }
                    }
                    log.trace("Bundle from "+bundleUrl+" already installed as "+nv+"; not re-registering");
                    return true;
                } else {
                    log.error("Bundle "+nv+" from "+bundleUrl+" is known in map but not installed; perhaps in the process of installing?");
                }
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

    public <T> Maybe<Class<T>> tryResolveClass(String type, CatalogBundle... catalogBundles) {
        return tryResolveClass(type, Arrays.asList(catalogBundles));
    }
    public <T> Maybe<Class<T>> tryResolveClass(String type, Iterable<CatalogBundle> catalogBundles) {
        Map<CatalogBundle,Throwable> bundleProblems = MutableMap.of();
        for (CatalogBundle catalogBundle: catalogBundles) {
            try {
                Maybe<Bundle> bundle = findBundle(catalogBundle);
                if (bundle.isPresent()) {
                    Bundle b = bundle.get();
                    Class<T> clazz;
                    //Extension bundles don't support loadClass.
                    //Instead load from the app classpath.
                    if (Osgis.isExtensionBundle(b)) {
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
                    bundleProblems.put(catalogBundle, ((Maybe.Absent<?>)bundle).getException());
                }
                
            } catch (Exception e) {
                // should come from classloading now; name formatting or missing bundle errors will be caught above 
                Exceptions.propagateIfFatal(e);
                bundleProblems.put(catalogBundle, e);

                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage().contains("Unresolved constraint in bundle")) {
                    log.warn("Unresolved constraint resolving OSGi bundle "+catalogBundle+" to load "+type+": "+cause.getMessage());
                    if (log.isDebugEnabled()) log.debug("Trace for OSGi resolution failure", e);
                }
            }
        }
        if (bundleProblems.size()==1) {
            Throwable error = Iterables.getOnlyElement(bundleProblems.values());
            if (error instanceof ClassNotFoundException && error.getCause()!=null && error.getCause().getMessage()!=null) {
                error = Exceptions.collapseIncludingAllCausalMessages(error);
            }
            return Maybe.absent("Unable to resolve class "+type+" in "+Iterables.getOnlyElement(bundleProblems.keySet()), error);
        } else {
            return Maybe.absent(Exceptions.create("Unable to resolve class "+type+": "+bundleProblems, bundleProblems.values()));
        }
    }

    public Maybe<Bundle> findBundle(CatalogBundle catalogBundle) {
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

    public URL getResource(String name, Iterable<CatalogBundle> catalogBundles) {
        for (CatalogBundle catalogBundle: catalogBundles) {
            try {
                Maybe<Bundle> bundle = findBundle(catalogBundle);
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

    public Framework getFramework() {
        return framework;
    }
    
}
