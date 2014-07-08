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
package brooklyn.entity.database.rubyrep;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name = "RubyRep Node", description = "RubyRep is a database replication system", iconUrl = "classpath:///rubyrep-logo.jpeg")
@ImplementedBy(RubyRepNodeImpl.class)
public interface RubyRepNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.0");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "http://files.rubyforge.vm.bytemark.co.uk/rubyrep/rubyrep-${version}.zip");

    @SetFromFlag("configurationScriptUrl")
    ConfigKey<String> CONFIGURATION_SCRIPT_URL = ConfigKeys.newStringConfigKey(
            "database.rubyrep.configScriptUrl",
            "URL where RubyRep configuration can be found - disables other configuration options (except version)");

    @SetFromFlag("templateUrl")
    ConfigKey<String> TEMPLATE_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "database.rubyrep.templateConfigurationUrl", "Template file (in freemarker format) for the rubyrep.conf file",
            "classpath://brooklyn/entity/database/rubyrep/rubyrep.conf");

    @SetFromFlag("tables")
    ConfigKey<String> TABLE_REGEXP = ConfigKeys.newStringConfigKey(
            "database.rubyrep.tableRegex", "Regular expression to select tables to sync using RubyRep", ".");

    @SetFromFlag("replicationInterval")
    ConfigKey<Integer> REPLICATION_INTERVAL = ConfigKeys.newIntegerConfigKey(
            "database.rubyrep.replicationInterval", "Replication Interval", 30);

    @SetFromFlag("startupTimeout")
    ConfigKey<Integer> DATABASE_STARTUP_TIMEOUT = ConfigKeys.newIntegerConfigKey(
            "database.rubyrep.startupTimeout", "Time to wait until databases have started up (in seconds)", 120);

    // Left database

    AttributeSensor<String> LEFT_DATASTORE_URL = Sensors.newSensorWithPrefix("left", DatastoreMixins.DATASTORE_URL);

    @SetFromFlag("leftDatabase")
    ConfigKey<? extends DatastoreCommon> LEFT_DATABASE = ConfigKeys.newConfigKey(DatastoreCommon.class,
            "database.rubyrep.leftDatabase", "Brooklyn database entity to use as the left DBMS");

    @SetFromFlag("leftDatabaseName")
    ConfigKey<String> LEFT_DATABASE_NAME = ConfigKeys.newStringConfigKey(
            "database.rubyrep.leftDatabaseName", "name of database to use for left db");

    @SetFromFlag("leftUsername")
    ConfigKey<String> LEFT_USERNAME = ConfigKeys.newStringConfigKey(
            "database.rubyrep.leftUsername", "username to connect to left db");

    @SetFromFlag("leftPassword")
    ConfigKey<String> LEFT_PASSWORD = ConfigKeys.newStringConfigKey(
            "database.rubyrep.leftPassword", "password to connect to left db");

    // Right database

    AttributeSensor<String> RIGHT_DATASTORE_URL = Sensors.newSensorWithPrefix("right", DatastoreMixins.DATASTORE_URL);

    @SetFromFlag("rightDatabase")
    ConfigKey<? extends DatastoreCommon> RIGHT_DATABASE = ConfigKeys.newConfigKey(DatastoreCommon.class,
            "database.rubyrep.rightDatabase", "Brooklyn database entity to use as the right DBMS");

    @SetFromFlag("rightDatabaseName")
    ConfigKey<String> RIGHT_DATABASE_NAME = ConfigKeys.newStringConfigKey(
            "database.rubyrep.rightDatabaseName", "name of database to use for right db");

    @SetFromFlag("rightUsername")
    ConfigKey<String> RIGHT_USERNAME = ConfigKeys.newStringConfigKey(
            "database.rubyrep.rightUsername", "username to connect to right db");

    @SetFromFlag("rightPassword")
    ConfigKey<String> RIGHT_PASSWORD = ConfigKeys.newStringConfigKey(
            "database.rubyrep.rightPassword", "password to connect to right db");

}
