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
package org.apache.brooklyn.core.util.osgi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

import org.apache.felix.framework.FrameworkFactory;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.core.util.ResourceUtils;
import org.apache.brooklyn.core.util.osgi.Osgis;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.ReferenceWithError;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;

/** 
 * utilities for working with osgi.
 * osgi support is in early days (June 2014) so this class is beta, subject to change,
 * particularly in how framework is started and bundles installed.
 * 
 * @since 0.7.0  */
@Beta
public class Osgis {
    private static final Logger LOG = LoggerFactory.getLogger(Osgis.class);

    private static final String EXTENSION_PROTOCOL = "system";
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final Set<String> SYSTEM_BUNDLES = MutableSet.of();

    public static class VersionedName {
        private final String symbolicName;
        private final Version version;
        public VersionedName(Bundle b) {
            this.symbolicName = b.getSymbolicName();
            this.version = b.getVersion();
        }
        public VersionedName(String symbolicName, Version version) {
            this.symbolicName = symbolicName;
            this.version = version;
        }
        @Override public String toString() {
            return symbolicName + ":" + Strings.toString(version);
        }
        public boolean equals(String sn, String v) {
            return symbolicName.equals(sn) && (version == null && v == null || version != null && version.toString().equals(v));
        }
        public boolean equals(String sn, Version v) {
            return symbolicName.equals(sn) && (version == null && v == null || version != null && version.equals(v));
        }
        public String getSymbolicName() {
            return symbolicName;
        }
        public Version getVersion() {
            return version;
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(symbolicName, version);
        }
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof VersionedName)) return false;
            VersionedName o = (VersionedName) other;
            return Objects.equal(symbolicName, o.symbolicName) && Objects.equal(version, o.version);
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
            
            Maybe<VersionedName> nv = parseOsgiIdentifier(symbolicNameOptionallyWithVersion);
            if (nv.isAbsent())
                throw new IllegalArgumentException("Cannot parse symbolic-name:version string '"+symbolicNameOptionallyWithVersion+"'");

            return id(nv.get());
        }

        private BundleFinder id(VersionedName nv) {
            symbolicName(nv.getSymbolicName());
            if (nv.getVersion() != null) {
                version(nv.getVersion().toString());
            }
            return this;
        }

        public BundleFinder bundle(CatalogBundle bundle) {
            if (bundle.isNamed()) {
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
        cfg.put(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_MULTIPLE);
        FrameworkFactory factory = newFrameworkFactory();

        Stopwatch timer = Stopwatch.createStarted();
        Framework framework = factory.newFramework(cfg);
        try {
            framework.init();
            installBootBundles(framework);
            framework.start();
        } catch (Exception e) {
            // framework bundle start exceptions are not interesting to caller...
            throw Exceptions.propagate(e);
        }
        LOG.debug("System bundles are: "+SYSTEM_BUNDLES);
        LOG.debug("OSGi framework started in " + Duration.of(timer));
        return framework;
    }

    private static void installBootBundles(Framework framework) {
        Stopwatch timer = Stopwatch.createStarted();
        LOG.debug("Installing OSGi boot bundles from "+Osgis.class.getClassLoader()+"...");
        Enumeration<URL> resources;
        try {
            resources = Osgis.class.getClassLoader().getResources(MANIFEST_PATH);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        BundleContext bundleContext = framework.getBundleContext();
        Map<String, Bundle> installedBundles = getInstalledBundlesById(bundleContext);
        while(resources.hasMoreElements()) {
            URL url = resources.nextElement();
            ReferenceWithError<?> installResult = installExtensionBundle(bundleContext, url, installedBundles, getVersionedId(framework));
            if (installResult.hasError() && !installResult.masksErrorIfPresent()) {
                // it's reported as a critical error, so warn here
                LOG.warn("Unable to install manifest from "+url+": "+installResult.getError(), installResult.getError());
            } else {
                Object result = installResult.getWithoutError();
                if (result instanceof Bundle) {
                    String v = getVersionedId( (Bundle)result );
                    SYSTEM_BUNDLES.add(v);
                    if (installResult.hasError()) {
                        LOG.debug(installResult.getError().getMessage()+(result!=null ? " ("+result+"/"+v+")" : ""));
                    } else {
                        LOG.debug("Installed "+v+" from "+url);
                    }
                } else if (installResult.hasError()) {
                    LOG.debug(installResult.getError().getMessage());
                }
            }
        }
        LOG.debug("Installed OSGi boot bundles in "+Time.makeTimeStringRounded(timer)+": "+Arrays.asList(framework.getBundleContext().getBundles()));
    }

    private static Map<String, Bundle> getInstalledBundlesById(BundleContext bundleContext) {
        Map<String, Bundle> installedBundles = new HashMap<String, Bundle>();
        Bundle[] bundles = bundleContext.getBundles();
        for (Bundle b : bundles) {
            installedBundles.put(getVersionedId(b), b);
        }
        return installedBundles;
    }

    /** Wraps the bundle if successful or already installed, wraps TRUE if it's the system entry,
     * wraps null if the bundle is already installed from somewhere else;
     * in all these cases <i>masking</i> an explanatory error if already installed or it's the system entry.
     * <p>
     * Returns an instance wrapping null and <i>throwing</i> an error if the bundle could not be installed.
     */
    private static ReferenceWithError<?> installExtensionBundle(BundleContext bundleContext, URL manifestUrl, Map<String, Bundle> installedBundles, String frameworkVersionedId) {
        //ignore http://felix.extensions:9/ system entry
        if("felix.extensions".equals(manifestUrl.getHost())) 
            return ReferenceWithError.newInstanceMaskingError(null, new IllegalArgumentException("Skipping install of internal extension bundle from "+manifestUrl));

        try {
            Manifest manifest = readManifest(manifestUrl);
            if (!isValidBundle(manifest)) 
                return ReferenceWithError.newInstanceMaskingError(null, new IllegalArgumentException("Resource at "+manifestUrl+" is not an OSGi bundle: no valid manifest"));
            
            String versionedId = getVersionedId(manifest);
            URL bundleUrl = ResourceUtils.getContainerUrl(manifestUrl, MANIFEST_PATH);

            Bundle existingBundle = installedBundles.get(versionedId);
            if (existingBundle != null) {
                if (!bundleUrl.equals(existingBundle.getLocation()) &&
                        //the framework bundle is always pre-installed, don't display duplicate info
                        !versionedId.equals(frameworkVersionedId)) {
                    return ReferenceWithError.newInstanceMaskingError(null, new IllegalArgumentException("Bundle "+versionedId+" (from manifest " + manifestUrl + ") is already installed, from " + existingBundle.getLocation()));
                }
                return ReferenceWithError.newInstanceMaskingError(existingBundle, new IllegalArgumentException("Bundle "+versionedId+" from manifest " + manifestUrl + " is already installed"));
            }
            
            byte[] jar = buildExtensionBundle(manifest);
            LOG.debug("Installing boot bundle " + bundleUrl);
            //mark the bundle as extension so we can detect it later using the "system:" protocol
            //(since we cannot access BundleImpl.isExtension)
            Bundle newBundle = bundleContext.installBundle(EXTENSION_PROTOCOL + ":" + bundleUrl.toString(), new ByteArrayInputStream(jar));
            installedBundles.put(versionedId, newBundle);
            return ReferenceWithError.newInstanceWithoutError(newBundle);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return ReferenceWithError.newInstanceThrowingError(null, 
                new IllegalStateException("Problem installing extension bundle " + manifestUrl + ": "+e, e));
        }
    }

    private static Manifest readManifest(URL manifestUrl) throws IOException {
        Manifest manifest;
        InputStream in = null;
        try {
            in = manifestUrl.openStream();
            manifest = new Manifest(in);
        } finally {
            if (in != null) {
                try {in.close();} 
                catch (Exception e) {};
            }
        }
        return manifest;
    }

    private static byte[] buildExtensionBundle(Manifest manifest) throws IOException {
        Attributes atts = manifest.getMainAttributes();

        //the following properties are invalid in extension bundles
        atts.remove(new Attributes.Name(Constants.IMPORT_PACKAGE));
        atts.remove(new Attributes.Name(Constants.REQUIRE_BUNDLE));
        atts.remove(new Attributes.Name(Constants.BUNDLE_NATIVECODE));
        atts.remove(new Attributes.Name(Constants.DYNAMICIMPORT_PACKAGE));
        atts.remove(new Attributes.Name(Constants.BUNDLE_ACTIVATOR));
        
        //mark as extension bundle
        atts.putValue(Constants.FRAGMENT_HOST, "system.bundle; extension:=framework");

        //create the jar containing the manifest
        ByteArrayOutputStream jar = new ByteArrayOutputStream();
        JarOutputStream out = new JarOutputStream(jar, manifest);
        out.close();
        return jar.toByteArray();
    }

    private static boolean isValidBundle(Manifest manifest) {
        Attributes atts = manifest.getMainAttributes();
        return atts.containsKey(new Attributes.Name(Constants.BUNDLE_MANIFESTVERSION));
    }

    private static String getVersionedId(Bundle b) {
        return b.getSymbolicName() + ":" + b.getVersion();
    }

    private static String getVersionedId(Manifest manifest) {
        Attributes atts = manifest.getMainAttributes();
        return atts.getValue(Constants.BUNDLE_SYMBOLICNAME) + ":" +
            atts.getValue(Constants.BUNDLE_VERSION);
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
        String versionedId = getVersionedId(manifest);
        for (Bundle installedBundle : framework.getBundleContext().getBundles()) {
            if (versionedId.equals(getVersionedId(installedBundle))) {
                if (SYSTEM_BUNDLES.contains(versionedId)) {
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
    
    public static boolean isExtensionBundle(Bundle bundle) {
        String location = bundle.getLocation();
        return location != null && 
                EXTENSION_PROTOCOL.equals(Urls.getProtocol(location));
    }

    /** Takes a string which might be of the form "symbolic-name" or "symbolic-name:version" (or something else entirely)
     * and returns a VersionedName. The versionedName.getVersion() will be null if if there was no version in the input
     * (or returning {@link Maybe#absent()} if not valid, with a suitable error message). */
    public static Maybe<VersionedName> parseOsgiIdentifier(String symbolicNameOptionalWithVersion) {
        if (Strings.isBlank(symbolicNameOptionalWithVersion))
            return Maybe.absent("OSGi identifier is blank");
        
        String[] parts = symbolicNameOptionalWithVersion.split(":");
        if (parts.length>2)
            return Maybe.absent("OSGi identifier has too many parts; max one ':' symbol");
        
        Version v = null;
        if (parts.length == 2) {
            try {
                v = Version.parseVersion(parts[1]);
            } catch (IllegalArgumentException e) {
                return Maybe.absent("OSGi identifier has invalid version string ("+e.getMessage()+")");
            }
        }
        
        return Maybe.of(new VersionedName(parts[0], v));
    }

    /**
     * The class is not used, staying for future reference.
     * Remove after OSGi transition is completed.
     */
    public static class ManifestHelper {
        
        private static ManifestParser parse;
        private Manifest manifest;
        private String source;

        private static final String WIRING_PACKAGE = PackageNamespace.PACKAGE_NAMESPACE;
        
        public static ManifestHelper forManifestContents(String contents) throws IOException, BundleException {
            ManifestHelper result = forManifest(Streams.newInputStreamWithContents(contents));
            result.source = contents;
            return result;
        }
        
        public static ManifestHelper forManifest(URL url) throws IOException, BundleException {
            InputStream in = null;
            try {
                in = url.openStream();
                return forManifest(in);
            } finally {
                if (in != null) in.close();
            }
        }
        
        public static ManifestHelper forManifest(InputStream in) throws IOException, BundleException {
            return forManifest(new Manifest(in));
        }

        public static ManifestHelper forManifest(Manifest manifest) throws BundleException {
            ManifestHelper result = new ManifestHelper();
            result.manifest = manifest;
            parse = new ManifestParser(null, null, null, new StringMap(manifest.getMainAttributes()));
            return result;
        }
        
        public String getSymbolicName() {
            return parse.getSymbolicName();
        }

        public Version getVersion() {
            return parse.getBundleVersion();
        }

        public String getSymbolicNameVersion() {
            return getSymbolicName()+":"+getVersion();
        }

        public List<String> getExportedPackages() {
            MutableList<String> result = MutableList.of();
            for (BundleCapability c: parse.getCapabilities()) {
                if (WIRING_PACKAGE.equals(c.getNamespace())) {
                    result.add((String)c.getAttributes().get(WIRING_PACKAGE));
                }
            }
            return result;
        }
        
        @Nullable public String getSource() {
            return source;
        }
        
        public Manifest getManifest() {
            return manifest;
        }
    }
    
}
