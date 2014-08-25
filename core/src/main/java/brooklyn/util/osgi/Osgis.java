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
package brooklyn.util.osgi;

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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.ReferenceWithError;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
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
        LOG.debug("OSGi framework started in " + Duration.of(timer));

        return framework;
    }

    private static void installBootBundles(Framework framework) {
        Enumeration<URL> resources;
        try {
            resources = Osgis.class.getClassLoader().getResources(MANIFEST_PATH);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        BundleContext bundleContext = framework.getBundleContext();
        Map<String, Bundle> installedBundles = getInstalledBundles(bundleContext);
        while(resources.hasMoreElements()) {
            URL url = resources.nextElement();
            ReferenceWithError<Boolean> installResult = installExtensionBundle(bundleContext, url, installedBundles, getVersionedId(framework));
            if (installResult.hasError()) {
                if (installResult.getWithoutError()) {
                    // true return code means it was installed or trivially not installed
                    if (LOG.isTraceEnabled())
                        LOG.trace(installResult.getError().getMessage());
                } else {
                    if (installResult.masksErrorIfPresent()) {
                        // if error is masked, then it's not so important (many of the bundles we are looking at won't have manifests)
                        LOG.debug(installResult.getError().getMessage());
                    } else {
                        // it's reported as a critical error, so warn here
                        LOG.warn("Unable to install manifest from "+url+": "+installResult.getError(), installResult.getError());
                    }
                }
            }
        }
    }

    private static Map<String, Bundle> getInstalledBundles(BundleContext bundleContext) {
        Map<String, Bundle> installedBundles = new HashMap<String, Bundle>();
        Bundle[] bundles = bundleContext.getBundles();
        for (Bundle b : bundles) {
            installedBundles.put(getVersionedId(b), b);
        }
        return installedBundles;
    }

    private static ReferenceWithError<Boolean> installExtensionBundle(BundleContext bundleContext, URL manifestUrl, Map<String, Bundle> installedBundles, String frameworkVersionedId) {
        //ignore http://felix.extensions:9/ system entry
        if("felix.extensions".equals(manifestUrl.getHost())) 
            return ReferenceWithError.newInstanceMaskingError(true, new IllegalArgumentException("Skiping install of internal extension bundle from "+manifestUrl));

        try {
            Manifest manifest = readManifest(manifestUrl);
            if (!isValidBundle(manifest)) 
                return ReferenceWithError.newInstanceMaskingError(false, new IllegalArgumentException("Resource at "+manifestUrl+" is not an OSGi bundle: no valid manifest"));
            
            String versionedId = getVersionedId(manifest);
            URL bundleUrl = ResourceUtils.getContainerUrl(manifestUrl, MANIFEST_PATH);

            Bundle existingBundle = installedBundles.get(versionedId);
            if (existingBundle != null) {
                if (!bundleUrl.equals(existingBundle.getLocation()) &&
                        //the framework bundle is always pre-installed, don't display duplicate info
                        !versionedId.equals(frameworkVersionedId)) {
                    return ReferenceWithError.newInstanceMaskingError(false, new IllegalArgumentException("Bundle "+versionedId+" (from manifest " + manifestUrl + ") is already installed, from " + existingBundle.getLocation()));
                }
                return ReferenceWithError.newInstanceMaskingError(true, new IllegalArgumentException("Bundle "+versionedId+" from manifest " + manifestUrl + " is already installed"));
            }
            
            byte[] jar = buildExtensionBundle(manifest);
            LOG.debug("Installing boot bundle " + bundleUrl);
            //mark the bundle as extension so we can detect it later using the "system:" protocol
            //(since we cannot access BundleImpl.isExtension)
            Bundle newBundle = bundleContext.installBundle(EXTENSION_PROTOCOL + ":" + bundleUrl.toString(), new ByteArrayInputStream(jar));
            installedBundles.put(versionedId, newBundle);
            return ReferenceWithError.newInstanceWithoutError(true);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return ReferenceWithError.newInstanceThrowingError(false, 
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

        //Note that in OSGi 4.3+ it could be possible to have the same version installed
        //multiple times in more advanced scenarios. In our case we don't support it.
        
        //Felix already assumes the stream is pointing to a Jar
        JarInputStream stream;
        try {
            stream = new JarInputStream(getUrlStream(url));
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        Manifest manifest = stream.getManifest();
        Streams.closeQuietly(stream);
        String versionedId = getVersionedId(manifest);
        for (Bundle installedBundle : framework.getBundleContext().getBundles()) {
            if (versionedId.equals(getVersionedId(installedBundle))) {
                return installedBundle;
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
            InputStream in = url.openStream();
            ManifestHelper helper = forManifest(in);
            in.close();
            return helper;
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
