/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.database.postgresql;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * PostgreSQL database node entity.
 * <p>
 * <ul>
 * <li>On OS X you may need to increase kernel/memory settings as described
 * <a href="http://willbryant.net/software/mac_os_x/postgres_initdb_fatal_shared_memory_error_on_leopard">here</a>.
 * <li>You will also need to enable passwordless sudo.
 * </ul>
 */
@Catalog(name="PostgreSQL Node", description="PostgreSQL is an object-relational database management system (ORDBMS)", iconUrl="classpath:///postgresql-logo.jpeg")
@ImplementedBy(PostgreSqlNodeImpl.class)
public interface PostgreSqlNode extends SoftwareProcess, DatastoreCommon {

    @SetFromFlag("configFileUrl")
    ConfigKey<String> AUTHENTICATION_CONFIGURATION_FILE_URL = ConfigKeys.newStringConfigKey(
            "postgresql.config.file.url", "URL where PostgreSQL configuration file can be found");

    @SetFromFlag("authConfigFileUrl")
    ConfigKey<String> CONFIGURATION_FILE_URL = ConfigKeys.newStringConfigKey(
            "postgresql.authConfig.file.url", "URL where PostgreSQL host-based authentication configuration file can be found");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey POSTGRESQL_PORT =
            new PortAttributeSensorAndConfigKey("postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));

    @SetFromFlag("disconnectOnStop")
    ConfigKey<Boolean> DISCONNECT_ON_STOP =
            ConfigKeys.newBooleanConfigKey("postgresql.disconnect.on.stop", "If true, PostgreSQL will immediately disconnet (pg_ctl -m immediate stop) all current connections when the node is stopped", true);

    @SetFromFlag("pollPeriod")
    ConfigKey<Long> POLL_PERIOD = ConfigKeys.newLongConfigKey(
            "postgresql.sensorpoll", "Poll period (in milliseconds)", 1000L);

    String executeScript(String commands);

}
