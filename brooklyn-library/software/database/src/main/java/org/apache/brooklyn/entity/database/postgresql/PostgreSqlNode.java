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
package org.apache.brooklyn.entity.database.postgresql;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.database.DatabaseNode;
import org.apache.brooklyn.entity.database.DatastoreMixins;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * PostgreSQL database node entity.
 * <p>
 * <ul>
 * <li>You may need to increase shared memory settings in the kernel depending on the setting of
 * the {@link #SHARED_MEMORY_BUFFER} key. The minimumm value is <em>128kB</em>. See the PostgreSQL
 * <a href="http://www.postgresql.org/docs/9.1/static/kernel-resources.html">documentation</a>.
 * <li>You will also need to enable passwordless sudo.
 * </ul>
 */
@Catalog(name="PostgreSQL Node", description="PostgreSQL is an object-relational database management system (ORDBMS)", iconUrl="classpath:///postgresql-logo-200px.png")
@ImplementedBy(PostgreSqlNodeImpl.class)
public interface PostgreSqlNode extends SoftwareProcess, HasShortName, DatastoreCommon, DatabaseNode {
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "9.3-1");//"9.1-4");

    @SetFromFlag("configFileUrl")
    ConfigKey<String> CONFIGURATION_FILE_URL = ConfigKeys.newStringConfigKey(
            "postgresql.config.file.url", "URL where PostgreSQL configuration file can be found; "
                + "if not supplied the blueprint uses the default and customises it");

    @SetFromFlag("authConfigFileUrl")
    ConfigKey<String> AUTHENTICATION_CONFIGURATION_FILE_URL = ConfigKeys.newStringConfigKey(
            "postgresql.authConfig.file.url", "URL where PostgreSQL host-based authentication configuration file can be found; "
                + "if not supplied the blueprint uses the default and customises it");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey POSTGRESQL_PORT = new PortAttributeSensorAndConfigKey(
            "postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));

    @SetFromFlag("sharedMemory")
    ConfigKey<String> SHARED_MEMORY = ConfigKeys.newStringConfigKey(
            "postgresql.sharedMemory", "Size of shared memory buffer (must specify as kB, MB or GB, minimum 128kB)", "4MB");

    @SetFromFlag("maxConnections")
    ConfigKey<Integer> MAX_CONNECTIONS = ConfigKeys.newIntegerConfigKey(
            "postgresql.maxConnections", "Maximum number of connections to the database", 100);

    @SetFromFlag("disconnectOnStop")
    ConfigKey<Boolean> DISCONNECT_ON_STOP = ConfigKeys.newBooleanConfigKey(
            "postgresql.disconnect.on.stop", "If true, PostgreSQL will immediately disconnet (pg_ctl -m immediate stop) all current connections when the node is stopped", true);

    @SetFromFlag("pollPeriod")
    ConfigKey<Long> POLL_PERIOD = ConfigKeys.newLongConfigKey(
            "postgresql.sensorpoll", "Poll period (in milliseconds)", 1000L);
    
    @SetFromFlag("initializeDB")
    ConfigKey<Boolean> INITIALIZE_DB = ConfigKeys.newBooleanConfigKey(
            "postgresql.initialize", "If true, PostgreSQL will create a new user and database", false);

    @SetFromFlag("username")
    BasicAttributeSensorAndConfigKey<String> USERNAME = new BasicAttributeSensorAndConfigKey<>(
            String.class, "postgresql.username", "Username of the database user");
    
    String DEFAULT_USERNAME = "postgresqluser";
    
    @SetFromFlag("password")
    BasicAttributeSensorAndConfigKey<String> PASSWORD = new BasicAttributeSensorAndConfigKey<>(
            String.class, "postgresql.password",
            "Password for the database user, auto-generated if not set");

    @SetFromFlag("database")
    BasicAttributeSensorAndConfigKey<String> DATABASE = new BasicAttributeSensorAndConfigKey<>(
            String.class, "postgresql.database", "Database to be used");
    
    String DEFAULT_DB_NAME = "db";

    Effector<String> EXECUTE_SCRIPT = Effectors.effector(DatastoreMixins.EXECUTE_SCRIPT)
            .description("Executes the given script contents using psql")
            .buildAbstract();

    Integer getPostgreSqlPort();
    String getSharedMemory();
    Integer getMaxConnections();

    String executeScript(String commands);

}
