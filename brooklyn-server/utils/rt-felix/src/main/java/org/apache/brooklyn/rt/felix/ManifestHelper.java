/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.brooklyn.rt.felix;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.Manifest;
import javax.annotation.Nullable;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;

/**
 * The class is not used, staying for future reference.
 * Remove after OSGi transition is completed.
 */
public class ManifestHelper {
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
            if (in != null) {
                in.close();
            }
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
        return getSymbolicName() + ":" + getVersion();
    }

    public List<String> getExportedPackages() {
        MutableList<String> result = MutableList.of();
        for (BundleCapability c : parse.getCapabilities()) {
            if (WIRING_PACKAGE.equals(c.getNamespace())) {
                result.add((String) c.getAttributes().get(WIRING_PACKAGE));
            }
        }
        return result;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    public Manifest getManifest() {
        return manifest;
    }

}
