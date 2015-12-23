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
package org.apache.brooklyn.util.osgi;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Simple OSGi utilities.
 * 
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class OsgiUtils {

    public static URL getContainerUrl(URL url, String resourceInThatDir) {
        //Switching from manual parsing of jar: and file: URLs to java provided functionality.
        //The old code was breaking on any Windows path and instead of fixing it, using
        //the provided Java APIs seemed like the better option since they are already tested
        //on multiple platforms.
        boolean isJar = "jar".equals(url.getProtocol());
        if(isJar) {
            try {
                //let java handle the parsing of jar URL, no network connection is established.
                //Strips the jar protocol:
                //  jar:file:/<path to jar>!<resourceInThatDir>
                //  becomes
                //  file:/<path to jar>
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                url = connection.getJarFileURL();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            //Remove the trailing resouceInThatDir path from the URL, thus getting the parent folder.
            String path = url.toString();
            int i = path.indexOf(resourceInThatDir);
            if (i==-1) throw new IllegalStateException("Resource path ("+resourceInThatDir+") not in url substring ("+url+")");
            String parent = path.substring(0, i);
            try {
                url = new URL(parent);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Resource ("+resourceInThatDir+") found at invalid URL parent (" + parent + ")", e);
            }
        }
        return url;
    }

    public static String getVersionedId(Manifest manifest) {
        Attributes atts = manifest.getMainAttributes();
        return atts.getValue(Constants.BUNDLE_SYMBOLICNAME) + ":" + atts.getValue(Constants.BUNDLE_VERSION);
    }

    public static String getVersionedId(Bundle b) {
        return b.getSymbolicName() + ":" + b.getVersion();
    }

    /** Takes a string which might be of the form "symbolic-name" or "symbolic-name:version" (or something else entirely)
     * and returns a VersionedName. The versionedName.getVersion() will be null if if there was no version in the input
     * (or returning {@link Maybe#absent()} if not valid, with a suitable error message). */
    public static Maybe<VersionedName> parseOsgiIdentifier(String symbolicNameOptionalWithVersion) {
        if (Strings.isBlank(symbolicNameOptionalWithVersion)) {
            return Maybe.absent("OSGi identifier is blank");
        }
        String[] parts = symbolicNameOptionalWithVersion.split(":");
        if (parts.length > 2) {
            return Maybe.absent("OSGi identifier has too many parts; max one ':' symbol");
        }
        Version v = null;
        if (parts.length == 2) {
            try {
                v = Version.parseVersion(parts[1]);
            } catch (IllegalArgumentException e) {
                return Maybe.absent("OSGi identifier has invalid version string (" + e.getMessage() + ")");
            }
        }
        return Maybe.of(new VersionedName(parts[0], v));
    }

}
