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
package org.apache.brooklyn.entity.network.bind;

import javax.annotation.concurrent.Immutable;

/**
 * Provides operating system-specific information for working with the Bind service.
 */
// Class would be package-private if Freemarker didn't complain vociferously.
@Immutable
public class BindOsSupport {

    // Likewise would make these package-private and have no getters if Freemarker was ok with it.
    private final String packageName;
    private final String serviceName;
    private final String rootConfigFile;
    private final String configDirectory;
    private final String workingDirectory;
    private final String rootZonesFile;
    private final String keysFile;

    private BindOsSupport(
            String packageName,
            String serviceName,
            String rootConfigFile,
            String configDirectory,
            String workingDirectory,
            String rootZonesFile,
            String keysFile) {
        this.packageName = packageName;
        this.serviceName = serviceName;
        this.rootConfigFile = rootConfigFile;
        this.configDirectory = configDirectory;
        this.workingDirectory = workingDirectory;
        this.rootZonesFile = rootZonesFile;
        this.keysFile = keysFile;
    }

    /**
     * @return support for RHEL-based operating systems.
     */
    public static BindOsSupport forRhel() {
        return new BindOsSupport(
                "bind",
                "named",
                "/etc/named.conf",
                "/var/named",
                "/var/named/data",
                "/var/named/named.ca",
                "/etc/named.iscdlv.key");
    }

    /**
     * @return support for Debian-based operating systems.
     */
    public static BindOsSupport forDebian() {
        return new BindOsSupport(
                "bind9",
                "bind9",
                "/etc/bind/named.conf",
                "/etc/bind",
                "/var/cache/bind",
                "/etc/bind/db.root",
                "/etc/bind/bind.keys"
        );
    }

    public String getPackageName() {
        return packageName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRootConfigFile() {
        return rootConfigFile;
    }

    public String getConfigDirectory() {
        return configDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getRootZonesFile() {
        return rootZonesFile;
    }

    public String getKeysFile() {
        return keysFile;
    }

}
