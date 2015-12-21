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

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.database.DatastoreMixins.HasDatastoreUrl;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.util.GenericTypes;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MySqlClusterImpl.class)
@Catalog(name="MySql Master-Slave cluster", description="Sets up a cluster of MySQL nodes using master-slave relation and binary logging", iconUrl="classpath:///mysql-logo-110x57.png")
public interface MySqlCluster extends DynamicCluster, HasDatastoreUrl {
    interface MySqlMaster {
        ConfigKey<String> MASTER_CREATION_SCRIPT_CONTENTS = ConfigKeys.newStringConfigKey(
                "datastore.master.creation.script.contents", "Contents of creation script to initialize the master node after initializing replication");

        ConfigKey<String> MASTER_CREATION_SCRIPT_URL = ConfigKeys.newStringConfigKey(
                "datastore.master.creation.script.url", "URL of creation script to use to initialize the master node after initializing replication (ignored if creationScriptContents is specified)");
    }
    interface MySqlSlave {
        AttributeSensor<Boolean> SLAVE_HEALTHY = Sensors.newBooleanSensor("mysql.slave.healthy", "Indicates that the replication state of the slave is healthy");
        AttributeSensor<Integer> SLAVE_SECONDS_BEHIND_MASTER = Sensors.newIntegerSensor("mysql.slave.seconds_behind_master", "How many seconds behind master is the replication state on the slave");
    }

    AttributeSensor<ReplicationSnapshot> REPLICATION_LAST_SLAVE_SNAPSHOT = Sensors.newSensor(ReplicationSnapshot.class, "mysql.replication.last_slave_snapshot", "Last valid state to init slaves with");
    ConfigKey<String> REPLICATION_PREFERRED_SOURCE = ConfigKeys.newStringConfigKey("mysql.replication.preferred_source", "ID of node to get the replication snapshot from. If not set a random slave is used, falling back to master if no slaves.");

    ConfigKey<String> SLAVE_USERNAME = ConfigKeys.newStringConfigKey(
            "mysql.slave.username", "The user name slaves will use to connect to the master", "slave");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_DO_DB = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_do_db", "Replicate only listed DBs. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_IGNORE_DB = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_ignore_db", "Don't replicate listed DBs. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_DO_TABLE = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_do_table", "Replicate only listed tables. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_IGNORE_TABLE = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_ignore_table", "Don't replicate listed tables. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_WILD_DO_TABLE = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_wild_do_table", "Replicate only listed tables, wildcards acepted. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_WILD_IGNORE_TABLE = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_wild_ignore_table", "Don't replicate listed tables, wildcards acepted. Use together with 'mysql.slave.replicate_dump_db'.");
    ConfigKey<Collection<String>> SLAVE_REPLICATE_DUMP_DB = ConfigKeys.newConfigKey(GenericTypes.COLLECTION_STRING,
            "mysql.slave.replicate_dump_db", "Databases to pass to the mysqldump command, used for slave initialization");
    StringAttributeSensorAndConfigKey SLAVE_PASSWORD = new StringAttributeSensorAndConfigKey(
            "mysql.slave.password", "The password slaves will use to connect to the master. Will be auto-generated by default.");
    @SuppressWarnings("serial")
    AttributeSensor<List<String>> SLAVE_DATASTORE_URL_LIST = Sensors.newSensor(new TypeToken<List<String>>() {},
            "mysql.slave.datastore.url", "List of all slave's DATASTORE_URL sensors");
    AttributeSensor<Double> QUERIES_PER_SECOND_FROM_MYSQL_PER_NODE = Sensors.newDoubleSensor("mysql.queries.perSec.fromMysql.perNode");
}
