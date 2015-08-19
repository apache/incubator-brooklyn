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
package org.apache.brooklyn.entity.database.mariadb;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.database.DatabaseNode;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.core.PortRanges;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

@Catalog(name="MariaDB Node", description="MariaDB is an open source relational database management system (RDBMS)", iconUrl="classpath:///mariadb-logo-180x119.png")
@ImplementedBy(MariaDbNodeImpl.class)
public interface MariaDbNode extends SoftwareProcess, DatastoreCommon, HasShortName, DatabaseNode {

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION =
        ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.5.40");

    // https://downloads.mariadb.org/interstitial/mariadb-5.5.33a/kvm-bintar-hardy-amd64/mariadb-5.5.33a-linux-x86_64.tar.gz/from/http://mirrors.coreix.net/mariadb
    // above redirects to download the artifactd from the URLs below.
    // Use `curl -sL -w "%{http_code} %{url_effective}\n" "http://..." -o target.tar.gz` to find out redirect URL.
    //     64-bit: http://mirrors.coreix.net/mariadb/mariadb-5.5.40/bintar-linux-x86_64/mariadb-5.5.40-linux-x86_64.tar.gz
    //     32-bit: http://mirrors.coreix.net/mariadb/mariadb-5.5.40/bintar-linux-x86/mariadb-5.5.40-linux-i686.tar.gz

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
          Attributes.DOWNLOAD_URL, "${driver.mirrorUrl}/mariadb-${version}/${driver.downloadParentDir}/mariadb-${version}-${driver.osTag}.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final ConfigKey<String> MIRROR_URL = ConfigKeys.newStringConfigKey("mariadb.install.mirror.url", "URL of mirror",
        "http://mirrors.coreix.net/mariadb/"
     );

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MARIADB_PORT =
        new PortAttributeSensorAndConfigKey("mariadb.port", "MariaDB port", PortRanges.fromString("3306, 13306+"));

    @SetFromFlag("dataDir")
    public static final ConfigKey<String> DATA_DIR = ConfigKeys.newStringConfigKey(
        "mariadb.datadir", "Directory for writing data files", null);

    @SetFromFlag("serverConf")
    public static final MapConfigKey<Object> MARIADB_SERVER_CONF = new MapConfigKey<Object>(
        Object.class, "mariadb.server.conf", "Configuration options for MariaDB server");

    public static final ConfigKey<Object> MARIADB_SERVER_CONF_LOWER_CASE_TABLE_NAMES =
        MARIADB_SERVER_CONF.subKey("lower_case_table_names", "See MariaDB (or MySQL!) guide. Set 1 to ignore case in table names (useful for OS portability)");

    @SetFromFlag("password")
    public static final StringAttributeSensorAndConfigKey PASSWORD = new StringAttributeSensorAndConfigKey(
        "mariadb.password", "Database admin password (or randomly generated if not set)", null);

    @SetFromFlag("socketUid")
    public static final StringAttributeSensorAndConfigKey SOCKET_UID = new StringAttributeSensorAndConfigKey(
        "mariadb.socketUid", "Socket uid, for use in file /tmp/mysql.sock.<uid>.3306 (or randomly generated if not set)", null);

    /** @deprecated since 0.7.0 use DATASTORE_URL */ @Deprecated
    public static final AttributeSensor<String> MARIADB_URL = DATASTORE_URL;

    @SetFromFlag("configurationTemplateUrl")
    static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new StringAttributeSensorAndConfigKey(
        "mariadb.template.configuration.url", "Template file (in freemarker format) for the my.cnf file",
        "classpath://org/apache/brooklyn/entity/database/mariadb/my.cnf");

    public static final AttributeSensor<Double> QUERIES_PER_SECOND_FROM_MARIADB =
        Sensors.newDoubleSensor("mariadb.queries.perSec.fromMariadb");

    public String executeScript(String commands);
}
