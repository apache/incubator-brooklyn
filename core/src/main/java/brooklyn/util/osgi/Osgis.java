/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.util.osgi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/** 
 * utilities for working with osgi.
 * osgi support is in early days (June 2014) so this class is beta, subject to change,
 * particularly in how framework is started and bundles installed.
 * 
 * @since 0.7.0  */
@Beta
public class Osgis {

    private static final Logger LOG = LoggerFactory.getLogger(Osgis.class);

    public static List<Bundle> getBundlesByName(Framework framework, String symbolicName, Predicate<Version> versionMatcher) {
        List<Bundle> result = MutableList.of();
        for (Bundle b: framework.getBundleContext().getBundles()) {
            if (symbolicName.equals(b.getSymbolicName()) && versionMatcher.apply(b.getVersion())) {
                result.add(b);
            }
        }
        return result;
    }

    public static List<Bundle> getBundlesByName(Framework framework, String symbolicName) {
        return getBundlesByName(framework, symbolicName, Predicates.<Version>alwaysTrue());
    }

    /**
     * Tries to find a bundle in the given framework with name matching either `name' or `name:version'.
     */
    public static Maybe<Bundle> getBundle(Framework framework, String symbolicNameOptionallyWithVersion) {
        String[] parts = symbolicNameOptionallyWithVersion.split(":");
        Maybe<Bundle> result = Maybe.absent("No bundles matching "+symbolicNameOptionallyWithVersion);
        if (parts.length == 2) {
            result = getBundle(framework, parts[0], parts[1]);
        } else if (parts.length == 1) {
            // TODO: Select latest version rather than first result
            List<Bundle> matches = getBundlesByName(framework, symbolicNameOptionallyWithVersion);
            if (!matches.isEmpty()) {
                result = Maybe.of(matches.iterator().next());
            }
        } else {
            throw new IllegalArgumentException("Cannot parse symbolic-name:version string '"+symbolicNameOptionallyWithVersion+"'");
        }
        return result;
    }
    
    public static Maybe<Bundle> getBundle(Framework framework, String symbolicName, String version) {
        return getBundle(framework, symbolicName, Version.parseVersion(version));
    }

    public static Maybe<Bundle> getBundle(Framework framework, String symbolicName, Version version) {
        List<Bundle> matches = getBundlesByName(framework, symbolicName, Predicates.equalTo(version));
        if (matches.isEmpty()) {
            return Maybe.absent("No bundles matching name=" + symbolicName + " version=" + version);
        } else if (matches.size() > 1) {
            LOG.warn("More than one bundle in framework={} matched name={}, version={}! Returning first of matches={}",
                    new Object[]{framework, symbolicName, version, Joiner.on(", ").join(matches)});
        }
        return Maybe.of(matches.iterator().next());
    }

    // -------- creating
    
    /*
     * loading framework factory and starting framework based on:
     * http://felix.apache.org/documentation/subprojects/apache-felix-framework/apache-felix-framework-launching-and-embedding.html
     */
    
    public static FrameworkFactory newFrameworkFactory() {
        URL url = Osgis.class.getClassLoader().getResource(
                "META-INF/services/org.osgi.framework.launch.FrameworkFactory");
        if (url != null) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                try {
                    for (String s = br.readLine(); s != null; s = br.readLine()) {
                        s = s.trim();
                        // load the first non-empty, non-commented line
                        if ((s.length() > 0) && (s.charAt(0) != '#')) {
                            return (FrameworkFactory) Class.forName(s).newInstance();
                        }
                    }
                } finally {
                    if (br != null) br.close();
                }
            } catch (Exception e) {
                // class creation exceptions are not interesting to caller...
                throw Exceptions.propagate(e);
            }
        }
        throw new IllegalStateException("Could not find framework factory.");
    }
    
    public static Framework newFrameworkStarted(String felixCacheDir, boolean clean, Map<?,?> extraStartupConfig) {
        Map<Object,Object> cfg = MutableMap.copyOf(extraStartupConfig);
        if (clean) cfg.put(Constants.FRAMEWORK_STORAGE_CLEAN, "onFirstInit");
        if (felixCacheDir!=null) cfg.put(Constants.FRAMEWORK_STORAGE, felixCacheDir);
        FrameworkFactory factory = newFrameworkFactory();
        
        Framework framework = factory.newFramework(cfg);
        try {
            framework.init();
            // nothing needs auto-loading, currently (and this needs a new dependency)
            // AutoProcessor.process(configProps, m_fwk.getBundleContext());
            framework.start();
        } catch (Exception e) {
            // framework bundle start exceptions are not interesting to caller...
            throw Exceptions.propagate(e);
        }
        return framework;
    }

    /**
     * Installs a bundle from the given URL, doing a check if already installed, and
     * using the {@link ResourceUtils} loader for this project (brooklyn core)
     */
    public static Bundle install(Framework framework, String url) throws BundleException {
        Bundle bundle = framework.getBundleContext().getBundle(url);
        if (bundle != null) {
            return bundle;
        }

        // use our URL resolution so we get classpath items
        LOG.debug("Installing bundle into {} from url: {}", framework, url);
        InputStream stream = ResourceUtils.create(Osgis.class).getResourceFromUrl(url);
        return framework.getBundleContext().installBundle(url, stream);
    }

}
