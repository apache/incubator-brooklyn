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

import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.os.Os.DeletionResult;
import brooklyn.util.osgi.Osgis;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;

public class OsgiManager {

    private static final Logger log = LoggerFactory.getLogger(OsgiManager.class);
    
    public static final ConfigKey<Boolean> USE_OSGI = BrooklynServerConfig.USE_OSGI;
    
    /* see Osgis for info on starting framework etc */
    
    protected Framework framework;
    protected File osgiTempDir;
    
    // we could manage without this map but it is useful to validate what is a user-supplied url
    protected Map<String,String> urlToBundleIdentifier = MutableMap.of();
    
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

    public void registerBundle(String bundleUrl) {
        try {
            String nv = urlToBundleIdentifier.get(bundleUrl);
            if (nv!=null) {
                if (Osgis.bundleFinder(framework).id(nv).requiringFromUrl(bundleUrl).find().isPresent()) {
                    log.trace("Bundle from "+bundleUrl+" already installed as "+nv+"; not re-registering");
                    return;
                } else {
                    log.debug("Bundle "+nv+" from "+bundleUrl+" is known in map but not installed; perhaps in the process of installing?");
                }
            }
            
            Bundle b = Osgis.install(framework, bundleUrl);
            nv = b.getSymbolicName()+":"+b.getVersion().toString();
            
            List<Bundle> matches = Osgis.bundleFinder(framework).id(nv).findAll();
            if (matches.isEmpty()) {
                log.error("OSGi could not find bundle "+nv+" in search after installing it from "+bundleUrl);
            } else if (matches.size()==1) {
                log.debug("Bundle from "+bundleUrl+" successfully installed as " + nv + " ("+b+")");
            } else {
                log.warn("OSGi has multiple bundles matching "+nv+", when just installed from "+bundleUrl+": "+matches+"; "
                    + "brooklyn will prefer the URL-based bundle for top-level references but any dependencies or "
                    + "import-packages will be at the mercy of OSGi. "
                    + "It is recommended to use distinct versions for different bundles, and the same URL for the same bundles.");
            }
            urlToBundleIdentifier.put(bundleUrl, nv);
            
        } catch (BundleException e) {
            log.debug("Bundle from "+bundleUrl+" failed to install (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    public <T> Maybe<Class<T>> tryResolveClass(String type, String... bundleUrlsOrNameVersionString) {
        return tryResolveClass(type, Arrays.asList(bundleUrlsOrNameVersionString));
    }
    public <T> Maybe<Class<T>> tryResolveClass(String type, Iterable<String> bundleUrlsOrNameVersionString) {
        Map<String,Throwable> bundleProblems = MutableMap.of();
        for (String bundleUrlOrNameVersionString: bundleUrlsOrNameVersionString) {
            try {
                Maybe<Bundle> bundle = findBundle(bundleUrlOrNameVersionString);
                
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
                    bundleProblems.put(bundleUrlOrNameVersionString, ((Maybe.Absent<?>)bundle).getException());
                }
                
            } catch (Exception e) {
                // should come from classloading now; name formatting or missing bundle errors will be caught above 
                Exceptions.propagateIfFatal(e);
                bundleProblems.put(bundleUrlOrNameVersionString, e);

                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage().contains("Unresolved constraint in bundle")) {
                    log.warn("Unresolved constraint resolving OSGi bundle "+bundleUrlOrNameVersionString+" to load "+type+": "+cause.getMessage());
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

    /** finds an installed bundle with the given URL or OSGi identifier ("symbolicName:version" string) */
    public Maybe<Bundle> findBundle(String bundleUrlOrNameVersionString) {
        String bundleNameVersion = urlToBundleIdentifier.get(bundleUrlOrNameVersionString);
        if (bundleNameVersion==null) {
            Maybe<String[]> nv = Osgis.parseOsgiIdentifier(bundleUrlOrNameVersionString);
            if (nv.isPresent())
                bundleNameVersion = bundleUrlOrNameVersionString;
        }
        Maybe<Bundle> bundle = Osgis.bundleFinder(framework).id(bundleNameVersion).preferringFromUrl(bundleUrlOrNameVersionString).find();
        return bundle;
    }

    public URL getResource(String name, Iterable<String> bundleUrlsOrNameVersionString) {
        for (String bundleUrlOrNameVersionString: bundleUrlsOrNameVersionString) {
            try {
                Maybe<Bundle> bundle = findBundle(bundleUrlOrNameVersionString);
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
