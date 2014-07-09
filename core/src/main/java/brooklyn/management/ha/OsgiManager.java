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

    public void registerBundle(String bundleUrl) {
        try {
            String nv = bundleUrlToNameVersionString.get(bundleUrl);
            if (nv!=null) {
                if (Osgis.getBundle(framework, nv).isPresent()) {
                    log.debug("Bundle from "+bundleUrl+" already installed as "+nv+"; not re-registering");
                    return;
                }
            }
            Bundle b = Osgis.install(framework, bundleUrl);
            log.debug("Bundle from "+bundleUrl+" successfully installed as "+nv);
            bundleUrlToNameVersionString.put(bundleUrl, b.getSymbolicName()+":"+b.getVersion().toString());
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
                String bundleNameVersion = bundleUrlToNameVersionString.get(bundleUrlOrNameVersionString);
                if (bundleNameVersion==null) {
                    bundleNameVersion = bundleUrlOrNameVersionString;
                }
                
                Maybe<Bundle> bundle = Osgis.getBundle(framework, bundleNameVersion);
                if (bundle.isPresent()) {
                    @SuppressWarnings("unchecked")
                    Class<T> clazz = (Class<T>) bundle.get().loadClass(type);
                    return Maybe.of(clazz);
                } else {
                    bundleProblems.put(bundleUrlOrNameVersionString, new IllegalStateException("Unable to find bundle "+bundleUrlOrNameVersionString));
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                bundleProblems.put(bundleUrlOrNameVersionString, e);
            }
        }
        return Maybe.absent("Unable to resolve class "+type+": "+bundleProblems);
    }

    public URL getResource(String name, Iterable<String> bundleUrlsOrNameVersionString) {
        for (String bundleUrlOrNameVersionString: bundleUrlsOrNameVersionString) {
            try {
                String bundleNameVersion = bundleUrlToNameVersionString.get(bundleUrlOrNameVersionString);
                if (bundleNameVersion==null) {
                    bundleNameVersion = bundleUrlOrNameVersionString;
                }
                
                Maybe<Bundle> bundle = Osgis.getBundle(framework, bundleNameVersion);
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
