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
package org.apache.brooklyn.util.core.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.rt.felix.EmbeddedFelixFramework;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.osgi.OsgiUtils;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
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

    /** @deprecated since 0.9.0, replaced with {@link org.apache.brooklyn.util.osgi.VersionedName} */
    @Deprecated
    public static class VersionedName extends org.apache.brooklyn.util.osgi.VersionedName {

        private VersionedName(org.apache.brooklyn.util.osgi.VersionedName src) {
            super(src.getSymbolicName(), src.getVersion());
        }

        public VersionedName(Bundle b) {
            super(b);
        }

        public VersionedName(String symbolicName, Version version) {
            super(symbolicName, version);
        }
    }

    public static class BundleFinder {
        protected final Framework framework;
        protected String symbolicName;
        protected String version;
        protected String url;
        protected boolean urlMandatory = false;
        protected final List<Predicate<? super Bundle>> predicates = MutableList.of();
        
        protected BundleFinder(Framework framework) {
            this.framework = framework;
        }

        public BundleFinder symbolicName(String symbolicName) {
            this.symbolicName = symbolicName;
            return this;
        }

        public BundleFinder version(String version) {
            this.version = version;
            return this;
        }
        
        public BundleFinder id(String symbolicNameOptionallyWithVersion) {
            if (Strings.isBlank(symbolicNameOptionallyWithVersion))
                return this;
            
            Maybe<org.apache.brooklyn.util.osgi.VersionedName> nv = OsgiUtils.parseOsgiIdentifier(symbolicNameOptionallyWithVersion);
            if (nv.isAbsent())
                throw new IllegalArgumentException("Cannot parse symbolic-name:version string '"+symbolicNameOptionallyWithVersion+"'");

            return id(nv.get());
        }

        private BundleFinder id(org.apache.brooklyn.util.osgi.VersionedName nv) {
            symbolicName(nv.getSymbolicName());
            if (nv.getVersion() != null) {
                version(nv.getVersion().toString());
            }
            return this;
        }

        public BundleFinder bundle(CatalogBundle bundle) {
            if (bundle.isNameResolved()) {
                symbolicName(bundle.getSymbolicName());
                version(bundle.getVersion());
            }
            if (bundle.getUrl() != null) {
                requiringFromUrl(bundle.getUrl());
            }
            return this;
        }

        /** Looks for a bundle matching the given URL;
         * unlike {@link #requiringFromUrl(String)} however, if the URL does not match any bundles
         * it will return other matching bundles <i>if</if> a {@link #symbolicName(String)} is specified.
         */
        public BundleFinder preferringFromUrl(String url) {
            this.url = url;
            urlMandatory = false;
            return this;
        }

        /** Requires the bundle to have the given URL set as its location. */
        public BundleFinder requiringFromUrl(String url) {
            this.url = url;
            urlMandatory = true;
            return this;
        }

        /** Finds the best matching bundle. */
        public Maybe<Bundle> find() {
            return findOne(false);
        }
        
        /** Finds the matching bundle, requiring it to be unique. */
        public Maybe<Bundle> findUnique() {
            return findOne(true);
        }

        protected Maybe<Bundle> findOne(boolean requireExactlyOne) {
            if (symbolicName==null && url==null)
                throw new IllegalStateException(this+" must be given either a symbolic name or a URL");
            
            List<Bundle> result = findAll();
            if (result.isEmpty())
                return Maybe.absent("No bundle matching "+getConstraintsDescription());
            if (requireExactlyOne && result.size()>1)
                return Maybe.absent("Multiple bundles ("+result.size()+") matching "+getConstraintsDescription());
            
            return Maybe.of(result.get(0));
        }
        
        /** Finds all matching bundles, in decreasing version order. */
        public List<Bundle> findAll() {
            boolean urlMatched = false;
            List<Bundle> result = MutableList.of();
            for (Bundle b: framework.getBundleContext().getBundles()) {
                if (symbolicName!=null && !symbolicName.equals(b.getSymbolicName())) continue;
                if (version!=null && !Version.parseVersion(version).equals(b.getVersion())) continue;
                for (Predicate<? super Bundle> predicate: predicates) {
                    if (!predicate.apply(b)) continue;
                }

                // check url last, because if it isn't mandatory we should only clear if we find a url
                // for which the other items also match
                if (url!=null) {
                    boolean matches = url.equals(b.getLocation());
                    if (urlMandatory) {
                        if (!matches) continue;
                        else urlMatched = true;
                    } else {
                        if (matches) {
                            if (!urlMatched) {
                                result.clear();
                                urlMatched = true;
                            }
                        } else {
                            if (urlMatched) {
                                // can't use this bundle as we have previously found a preferred bundle, with a matching url
                                continue;
                            }
                        }
                    }
                }
                                
                result.add(b);
            }
            
            if (symbolicName==null && url!=null && !urlMatched) {
                // if we only "preferred" the url, and we did not match it, and we did not have a symbolic name,
                // then clear the results list!
                result.clear();
            }

            Collections.sort(result, new Comparator<Bundle>() {
                @Override
                public int compare(Bundle o1, Bundle o2) {
                    return o2.getVersion().compareTo(o1.getVersion());
                }
            });
            
            return result;
        }
        
        public String getConstraintsDescription() {
            List<String> parts = MutableList.of();
            if (symbolicName!=null) parts.add("symbolicName="+symbolicName);
            if (version!=null) parts.add("version="+version);
            if (url!=null)
                parts.add("url["+(urlMandatory ? "required" : "preferred")+"]="+url);
            if (!predicates.isEmpty())
                parts.add("predicates="+predicates);
            return Joiner.on(";").join(parts);
        }
        
        @Override
        public String toString() {
            return getClass().getCanonicalName()+"["+getConstraintsDescription()+"]";
        }

        public BundleFinder version(final Predicate<Version> versionPredicate) {
            return satisfying(new Predicate<Bundle>() {
                @Override
                public boolean apply(Bundle input) {
                    return versionPredicate.apply(input.getVersion());
                }
            });
        }
        
        public BundleFinder satisfying(Predicate<? super Bundle> predicate) {
            predicates.add(predicate);
            return this;
        }
    }
    
    public static BundleFinder bundleFinder(Framework framework) {
        return new BundleFinder(framework);
    }

    /** @deprecated since 0.7.0 use {@link #bundleFinder(Framework)} */ @Deprecated
    public static List<Bundle> getBundlesByName(Framework framework, String symbolicName, Predicate<Version> versionMatcher) {
        return bundleFinder(framework).symbolicName(symbolicName).version(versionMatcher).findAll();
    }

    /** @deprecated since 0.7.0 use {@link #bundleFinder(Framework)} */ @Deprecated
    public static List<Bundle> getBundlesByName(Framework framework, String symbolicName) {
        return bundleFinder(framework).symbolicName(symbolicName).findAll();
    }

    /**
     * Tries to find a bundle in the given framework with name matching either `name' or `name:version'.
     * @deprecated since 0.7.0 use {@link #bundleFinder(Framework)} */ @Deprecated
    public static Maybe<Bundle> getBundle(Framework framework, String symbolicNameOptionallyWithVersion) {
        return bundleFinder(framework).id(symbolicNameOptionallyWithVersion).find();
    }
    
    /** @deprecated since 0.7.0 use {@link #bundleFinder(Framework)} */ @Deprecated
    public static Maybe<Bundle> getBundle(Framework framework, String symbolicName, String version) {
        return bundleFinder(framework).symbolicName(symbolicName).version(version).find();
    }

    /** @deprecated since 0.7.0 use {@link #bundleFinder(Framework)} */ @Deprecated
    public static Maybe<Bundle> getBundle(Framework framework, String symbolicName, Version version) {
        return bundleFinder(framework).symbolicName(symbolicName).version(Predicates.equalTo(version)).findUnique();
    }

    /** @deprecated since 0.9.0, replaced by {@link EmbeddedFelixFramework#newFrameworkFactory() */
    @Deprecated
    public static FrameworkFactory newFrameworkFactory() {
        return EmbeddedFelixFramework.newFrameworkFactory();
    }

    /** @deprecated since 0.9.0, replaced by {@link #getFramework(java.lang.String, boolean) } */
    @Deprecated
    public static Framework newFrameworkStarted(String felixCacheDir, boolean clean, Map<?,?> extraStartupConfig) {
        return getFramework(felixCacheDir, clean);
    }

    /** 
     * Provides an OSGI framework.
     *
     * When running inside an OSGi container, the container framework is returned.
     * When running standalone a new Apache Felix container is created.
     * 
     * Calling {@link #ungetFramework(Framework) } is needed in both cases, either to stop
     * the embedded framework or to release the service reference.
     *
     * @param felixCacheDir
     * @param clean
     * @return
     * @todo Use felixCacheDir ?
     */
    public static Framework getFramework(String felixCacheDir, boolean clean) {
        final Bundle bundle = FrameworkUtil.getBundle(Osgis.class);
        if (bundle != null) {
            // already running inside an OSGi container
            return (Framework) bundle.getBundleContext().getBundle(0);
        } else {
            // not running inside OSGi container
            return EmbeddedFelixFramework.newFrameworkStarted(felixCacheDir, clean, null);
        }
    }

    /**
     * Stops/ungets the OSGi framework.
     *
     * See {@link #getFramework(java.lang.String, boolean)}
     *
     * @param framework
     */
    public static void ungetFramework(Framework framework) {
        final Bundle bundle = FrameworkUtil.getBundle(Osgis.class);
        if (bundle != null) {
//            // already running inside an OSGi container
//            final BundleContext ctx = bundle.getBundleContext();
//            final ServiceReference<Framework> ref = ctx.getServiceReference(Framework.class);
//            ctx.ungetService(ref);
        } else {
            EmbeddedFelixFramework.stopFramework(framework);
        }
    }


    /** Tells if Brooklyn is running in an OSGi environment or not. */
    public static boolean isBrooklynInsideFramework() {
        return FrameworkUtil.getBundle(Osgis.class) != null;
    }

    /** @deprecated since 0.9.0, replaced with {@link OsgiUtils#getVersionedId(org.osgi.framework.Bundle) } */
    @Deprecated
    public static String getVersionedId(Bundle b) {
        return OsgiUtils.getVersionedId(b);
    }

    /** @deprecated since 0.9.0, replaced with {@link OsgiUtils#getVersionedId(java.util.jar.Manifest) } */
    @Deprecated
    public static String getVersionedId(Manifest manifest) {
        return OsgiUtils.getVersionedId(manifest);
    }

    /**
     * Installs a bundle from the given URL, doing a check if already installed, and
     * using the {@link ResourceUtils} loader for this project (brooklyn core)
     */
    public static Bundle install(Framework framework, String url) throws BundleException {
        boolean isLocal = isLocalUrl(url);
        String localUrl = url;
        if (!isLocal) {
            localUrl = cacheFile(url);
        }

        try {
            Bundle bundle = getInstalledBundle(framework, localUrl);
            if (bundle != null) {
                return bundle;
            }
    
            // use our URL resolution so we get classpath items
            LOG.debug("Installing bundle into {} from url: {}", framework, url);
            InputStream stream = getUrlStream(localUrl);
            Bundle installedBundle = framework.getBundleContext().installBundle(url, stream);
            
            return installedBundle;
        } finally {
            if (!isLocal) {
                try {
                    new File(new URI(localUrl)).delete();
                } catch (URISyntaxException e) {
                    throw Exceptions.propagate(e);
                }
            }
        }
    }

    private static String cacheFile(String url) {
        InputStream in = getUrlStream(url);
        File cache = Os.writeToTempFile(in, "bundle-cache", "jar");
        return cache.toURI().toString();
    }

    private static boolean isLocalUrl(String url) {
        String protocol = Urls.getProtocol(url);
        return "file".equals(protocol) ||
                "classpath".equals(protocol) ||
                "jar".equals(protocol);
    }

    private static Bundle getInstalledBundle(Framework framework, String url) {
        Bundle bundle = framework.getBundleContext().getBundle(url);
        if (bundle != null) {
            return bundle;
        }

        // We now support same version installed multiple times (avail since OSGi 4.3+).
        // However we do not support overriding *system* bundles, ie anything already on the classpath.
        // If we wanted to disable multiple versions, see comments below, and reference to FRAMEWORK_BSNVERSION_MULTIPLE above.
        
        // Felix already assumes the stream is pointing to a JAR
        JarInputStream stream;
        try {
            stream = new JarInputStream(getUrlStream(url));
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        Manifest manifest = stream.getManifest();
        Streams.closeQuietly(stream);
        if (manifest == null) {
            throw new IllegalStateException("Missing manifest file in bundle or not a jar file.");
        }
        String versionedId = OsgiUtils.getVersionedId(manifest);
        for (Bundle installedBundle : framework.getBundleContext().getBundles()) {
            if (versionedId.equals(OsgiUtils.getVersionedId(installedBundle))) {
                if (EmbeddedFelixFramework.isSystemBundle(installedBundle)) {
                    LOG.debug("Already have system bundle "+versionedId+" from "+installedBundle+"/"+installedBundle.getLocation()+" when requested "+url+"; not installing");
                    // "System bundles" (ie things on the classpath) cannot be overridden
                    return installedBundle;
                } else {
                    LOG.debug("Already have bundle "+versionedId+" from "+installedBundle+"/"+installedBundle.getLocation()+" when requested "+url+"; but it is not a system bundle so proceeding");
                    // Other bundles can be installed multiple times. To ignore multiples and continue to use the old one, 
                    // just return the installedBundle as done just above for system bundles.
                }
            }
        }
        return null;
    }

    private static InputStream getUrlStream(String url) {
        return ResourceUtils.create(Osgis.class).getResourceFromUrl(url);
    }
    
    /** @deprecated since 0.9.0, replaced with {@link EmbeddedFelixFramework#isExtensionBundle(Bundle)} */
    @Deprecated
    public static boolean isExtensionBundle(Bundle bundle) {
        return EmbeddedFelixFramework.isExtensionBundle(bundle);
    }

    /** @deprecated since 0.9.0, replaced with {@link OsgiUtils#parseOsgiIdentifier(java.lang.String) } */
    @Deprecated
    public static Maybe<VersionedName> parseOsgiIdentifier(String symbolicNameOptionalWithVersion) {
        final Maybe<org.apache.brooklyn.util.osgi.VersionedName> original = OsgiUtils.parseOsgiIdentifier(symbolicNameOptionalWithVersion);
        return original.transform(new Function<org.apache.brooklyn.util.osgi.VersionedName, VersionedName>() {
            @Override
            public VersionedName apply(org.apache.brooklyn.util.osgi.VersionedName input) {
                return new VersionedName(input);
            }
        });
    }

    /** @deprecated since 0.9.0, replaced with {@link org.apache.brooklyn.rt.felix.ManifestHelper} */
    @Deprecated
    public static class ManifestHelper extends org.apache.brooklyn.rt.felix.ManifestHelper {

    }
}
