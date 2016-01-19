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
package org.apache.brooklyn.entity.database.mysql;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

@Catalog(name="MySql Node", description="MySql is an open source relational database management system (RDBMS)", iconUrl="classpath:///mysql-logo-110x57.png")
@ImplementedBy(MySqlNodeImpl.class)
public interface MySqlNode extends SoftwareProcess, HasShortName, DatastoreCommon {

    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.6.26");

    //http://dev.mysql.com/get/Downloads/MySQL-5.6/mysql-5.6.26-osx10.9-x86_64.tar.gz
    //http://dev.mysql.com/get/Downloads/MySQL-5.6/mysql-5.6.26-linux-glibc2.5-x86_64.tar.gz
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "http://dev.mysql.com/get/Downloads/MySQL-${driver.majorVersion}/mysql-${version}-${driver.osTag}.tar.gz");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey MYSQL_PORT = new PortAttributeSensorAndConfigKey("mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+"));

    @SetFromFlag("dataDir")
    ConfigKey<String> DATA_DIR = ConfigKeys.newStringConfigKey(
            "mysql.datadir", "Directory for writing data files", null);

    @SetFromFlag("serverConf")
    MapConfigKey<Object> MYSQL_SERVER_CONF = new MapConfigKey<Object>(
            Object.class, "mysql.server.conf", "Configuration options for mysqld");
    
    ConfigKey<Object> MYSQL_SERVER_CONF_LOWER_CASE_TABLE_NAMES = MYSQL_SERVER_CONF.subKey("lower_case_table_names", "See MySQL guide. Set 1 to ignore case in table names (useful for OS portability)");
    
    @SetFromFlag("serverId")
    ConfigKey<Integer> MYSQL_SERVER_ID = ConfigKeys.newIntegerConfigKey("mysql.server_id", "Corresponds to server_id option", 0);
    
    @SetFromFlag("password")
    StringAttributeSensorAndConfigKey PASSWORD = new StringAttributeSensorAndConfigKey(
            "mysql.password", "Database admin password (or randomly generated if not set)", null);

    @SetFromFlag("socketUid")
    StringAttributeSensorAndConfigKey SOCKET_UID = new StringAttributeSensorAndConfigKey(
            "mysql.socketUid", "Socket uid, for use in file /tmp/mysql.sock.<uid>.3306 (or randomly generated if not set)", null);

    @SetFromFlag("generalLog")
    ConfigKey GENERAL_LOG = ConfigKeys.newBooleanConfigKey("mysql.general_log", "Enable general log", false);
    
    /** @deprecated since 0.7.0 use DATASTORE_URL */ @Deprecated
    AttributeSensor<String> MYSQL_URL = DATASTORE_URL;

    @SetFromFlag("configurationTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new StringAttributeSensorAndConfigKey(
            "mysql.template.configuration.url", "Template file (in freemarker format) for the mysql.conf file",
            "classpath://org/apache/brooklyn/entity/database/mysql/mysql.conf");

    AttributeSensor<Double> QUERIES_PER_SECOND_FROM_MYSQL = Sensors.newDoubleSensor("mysql.queries.perSec.fromMysql");

    interface ExportDumpEffector {
        ConfigKey<String> PATH = ConfigKeys.newStringConfigKey("path", "Where to export the dump to. Resolved against runtime directory if relative.", "dump.sql");
        ConfigKey<String> ADDITIONAL_OPTIONS = ConfigKeys.newStringConfigKey("additionalOptions", "Additional command line options to pass to mysqldump");

        Effector<Void> EXPORT_DUMP = Effectors.effector(Void.class, "export_dump")
                .description("Invokes mysqldump against the node")
                .parameter(PATH)
                .parameter(ADDITIONAL_OPTIONS)
                .buildAbstract();
    }
    Effector<Void> EXPORT_DUMP = ExportDumpEffector.EXPORT_DUMP;

    interface ImportDumpEffector {
        ConfigKey<String> PATH = ConfigKeys.newStringConfigKey("path", "Path to a file with SQL statements to import as the root user");

        Effector<Void> IMPORT_DUMP = Effectors.effector(Void.class, "import_dump")
                .description("Runs the sql statements in the file as the root user")
                .parameter(PATH)
                .buildAbstract();
    }
    Effector<Void> IMPORT_DUMP = ImportDumpEffector.IMPORT_DUMP;

    interface ChangePasswordEffector {
        ConfigKey<String> PASSWORD = ConfigKeys.newStringConfigKey("password", "New password to set");

        Effector<Void> CHANGE_PASSWORD = Effectors.effector(Void.class, "change_password")
                .description("Change the mysql root password")
                .parameter(PASSWORD)
                .buildAbstract();
    }
    Effector<Void> CHANGE_PASSWORD = ChangePasswordEffector.CHANGE_PASSWORD;

    @org.apache.brooklyn.core.annotation.Effector(description = "Execute SQL script on the node as the root user")
    public String executeScript(@EffectorParam(name="commands") String commands);

}
