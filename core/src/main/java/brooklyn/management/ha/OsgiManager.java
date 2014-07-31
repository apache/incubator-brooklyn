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
import brooklyn.util.osgi.Osgis;

import com.google.common.base.Throwables;

public class OsgiManager {

    private static final Logger log = LoggerFactory.getLogger(OsgiManager.class);
    
    public static final ConfigKey<Boolean> USE_OSGI = BrooklynServerConfig.USE_OSGI;
    
    /* see Osgis for info on starting framework etc */
    
    protected Framework framework;
    protected File osgiTempDir;
    protected Map<String,String> bundleUrlToNameVersionString = MutableMap.of();
    
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
        osgiTempDir = Os.deleteRecursively(osgiTempDir).asNullOrThrowing();
        framework = null;
    }

    public void registerBundle(CatalogBundle bundle) {
        try {
            Maybe<Bundle> osgiBundle = getRegisteredBundle(bundle);
            if(osgiBundle.isPresent()) {
                log.debug("Bundle " + osgiBundle + " already installed; not re-registering");
                return;
            } else if (bundle.getUrl() == null) {
                throw new IllegalStateException("Bundle " + bundle + " not already registered by name:version, but URL is empty.");
            }

            Bundle b = Osgis.install(framework, bundle.getUrl());
            log.debug("Bundle from "+bundle+" successfully installed.");
            bundleUrlToNameVersionString.put(bundle.getUrl(), b.getSymbolicName()+":"+b.getVersion().toString());
        } catch (BundleException e) {
            log.debug("Bundle from "+bundle+" failed to install (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    private Maybe<Bundle> getRegisteredBundle(CatalogBundle catalogBundle) {
        if (catalogBundle.isNamed()) {
            Maybe<Bundle> osgiBundle = Osgis.getBundle(framework, catalogBundle.getName(), catalogBundle.getVersion());
            if (osgiBundle.isPresent()) {
                return osgiBundle;
            }
        }
        
        if (catalogBundle.getUrl() != null) {
            String nv = bundleUrlToNameVersionString.get(catalogBundle.getUrl());
            if (nv!=null) {
                Maybe<Bundle> osgiBundle = Osgis.getBundle(framework, nv);
                if (osgiBundle.isPresent()) {
                    return osgiBundle;
                }
            }
        }
        
        return Maybe.absent("The bundle " + catalogBundle + " is not registered.");
    }

    public <T, BundleType extends CatalogBundle> Maybe<Class<T>> tryResolveClass(String type, BundleType... catalogBundles) {
        return tryResolveClass(type, Arrays.asList(catalogBundles));
    }
    public <T, BundleType extends CatalogBundle> Maybe<Class<T>> tryResolveClass(String type, Iterable<BundleType> catalogBundles) {
        Map<CatalogBundle,Throwable> bundleProblems = MutableMap.of();
        for (CatalogBundle catalogBundle: catalogBundles) {
            try {
                Maybe<Bundle> bundle = getRegisteredBundle(catalogBundle);
                if (bundle.isPresent()) {
                    @SuppressWarnings("unchecked")
                    Class<T> clazz = (Class<T>) bundle.get().loadClass(type);
                    return Maybe.of(clazz);
                } else {
                    bundleProblems.put(catalogBundle, new IllegalStateException("Unable to find bundle "+catalogBundle));
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                bundleProblems.put(catalogBundle, e);
            }
        }
        return Maybe.absent("Unable to resolve class "+type+": "+bundleProblems);
    }

    public URL getResource(String name, Iterable<CatalogBundle> catalogBundles) {
        for (CatalogBundle catalogBundle: catalogBundles) {
            try {
                Maybe<Bundle> bundle = getRegisteredBundle(catalogBundle);
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

}
