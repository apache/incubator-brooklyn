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

import com.google.common.base.Objects;
import org.apache.brooklyn.util.text.Strings;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * Versioned name of an OSGi bundle.
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class VersionedName {
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

    @Override
    public String toString() {
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
        if (!(other instanceof VersionedName)) {
            return false;
        }
        VersionedName o = (VersionedName) other;
        return Objects.equal(symbolicName, o.symbolicName) && Objects.equal(version, o.version);
    }

}
