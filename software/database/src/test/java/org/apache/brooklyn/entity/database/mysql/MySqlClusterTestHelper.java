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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.entity.database.mysql.MySqlCluster.MySqlMaster;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * Runs a slightly modified version of the popular Vogella MySQL tutorial,
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class MySqlClusterTestHelper {
    public static final Logger log = LoggerFactory.getLogger(MySqlClusterTestHelper.class);

    // From http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT = Joiner.on("\n").join(ImmutableList.of(
            "CREATE DATABASE feedback;",
            "CREATE USER 'sqluser'@'localhost' IDENTIFIED BY 'sqluserpw';",
            "GRANT USAGE ON *.* TO 'sqluser'@'localhost';",
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'localhost';",
            "CREATE USER 'sqluser'@'%' IDENTIFIED BY 'sqluserpw';",
            "GRANT USAGE ON *.* TO 'sqluser'@'%';",
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'%';",
            "FLUSH PRIVILEGES;",
            "USE feedback;",
            "CREATE TABLE COMMENTS (",
            "        id INT NOT NULL AUTO_INCREMENT,", 
            "        MYUSER VARCHAR(30) NOT NULL,",
            "        EMAIL VARCHAR(30), ",
            "        WEBPAGE VARCHAR(100) NOT NULL,", 
            "        DATUM DATE NOT NULL, ",
            "        SUMMARY VARCHAR(40) NOT NULL,",
            "        COMMENTS VARCHAR(400) NOT NULL,",
            "        PRIMARY KEY (ID)",
            "    );",
            "",
            "INSERT INTO COMMENTS values (default, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );",
            "",
            "CREATE DATABASE items;",
            "GRANT ALL PRIVILEGES ON items.* TO 'sqluser'@'localhost';",
            "GRANT ALL PRIVILEGES ON items.* TO 'sqluser'@'%';",
            "FLUSH PRIVILEGES;",
            "",
            "USE items;",
            "CREATE TABLE INVENTORY (MYUSER VARCHAR(30) NOT NULL);",
            "INSERT INTO INVENTORY values ('lars');",
            "",
            "CREATE DATABASE db_filter_test;",
            "USE db_filter_test;",
            "CREATE TABLE FILTERED (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (ID));"
            ));

    public static void test(TestApplication app, Location location) throws Exception {
        test(app, location, EntitySpec.create(MySqlCluster.class)
                .configure(MySqlCluster.INITIAL_SIZE, 2)
                .configure(MySqlNode.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(MySqlNode.MYSQL_SERVER_CONF, minimalMemoryConfig()));
    }

    public static MutableMap<String, Object> minimalMemoryConfig() {
        // http://www.tocker.ca/2014/03/10/configuring-mysql-to-use-minimal-memory.html
        return MutableMap.<String, Object>of()
                .add("skip-name-resolve","")
                .add("performance_schema","0")
                .add("innodb_buffer_pool_size","5M")
                .add("innodb_log_buffer_size","256K")
                .add("query_cache_size","0")
                .add("max_connections","10")
                .add("key_buffer_size","8")
                .add("thread_cache_size","0")
                .add("host_cache_size","0")
                .add("innodb_ft_cache_size","1600000")
                .add("innodb_ft_total_cache_size","32000000")

                // per thread or per operation settings
                .add("thread_stack","131072")
                .add("sort_buffer_size","32K")
                .add("read_buffer_size","8200")
                .add("read_rnd_buffer_size","8200")
                .add("max_heap_table_size","16K")
                .add("tmp_table_size","1K")
                .add("bulk_insert_buffer_size","0")
                .add("join_buffer_size","128")
                .add("net_buffer_length","1K")
                .add("innodb_sort_buffer_size","64K")

                // settings that relate to the binary log (if enabled)
                .add("binlog_cache_size","4K")
                .add("binlog_stmt_cache_size","4K");
    }

    public static void testMasterInit(TestApplication app, Location location) throws Exception {
        test(app, location, EntitySpec.create(MySqlCluster.class)
                .configure(MySqlCluster.INITIAL_SIZE, 2)
                .configure(MySqlMaster.MASTER_CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(MySqlNode.MYSQL_SERVER_CONF, minimalMemoryConfig()));
    }

    public static void test(TestApplication app, Location location, EntitySpec<MySqlCluster> clusterSpec) throws Exception {
        MySqlCluster cluster = initCluster(app, location, clusterSpec);
        MySqlNode master = (MySqlNode) cluster.getAttribute(MySqlCluster.FIRST);
        MySqlNode slave = (MySqlNode) Iterables.find(cluster.getMembers(), Predicates.not(Predicates.<Entity>equalTo(master)));
        assertEquals(cluster.getMembers().size(), 2);
        assertEquals(cluster.getAttribute(MySqlCluster.SLAVE_DATASTORE_URL_LIST).size(), 1);
        assertEquals(cluster.getAttribute(MySqlNode.DATASTORE_URL), master.getAttribute(MySqlNode.DATASTORE_URL));
        assertReplication(master, slave);
    }

    public static void assertReplication(MySqlNode master, MySqlNode slave, String... notReplicatedSchemas) throws ClassNotFoundException, Exception {
        VogellaExampleAccess masterDb = new VogellaExampleAccess("com.mysql.jdbc.Driver", master.getAttribute(MySqlNode.DATASTORE_URL));
        VogellaExampleAccess slaveDb = new VogellaExampleAccess("com.mysql.jdbc.Driver", slave.getAttribute(MySqlNode.DATASTORE_URL));
        masterDb.connect();
        slaveDb.connect();

        assertSlave(masterDb, slaveDb, 1);
        masterDb.modifyDataBase();
        masterDb.execute("items", "INSERT INTO INVENTORY values (?);", "Test");
        assertSlave(masterDb, slaveDb, 2);
        masterDb.revertDatabase();
        masterDb.execute("items", "delete from INVENTORY where myuser= ?;", "Test");
        assertSlave(masterDb, slaveDb, 1);

        Set<String> dbSchemas = slaveDb.getSchemas();
        for (String schema : notReplicatedSchemas) {
            assertFalse(dbSchemas.contains(schema), "Database " + schema + " exists on slave");
        }

        masterDb.close();
        slaveDb.close();

        log.info("Ran vogella MySQL example -- SUCCESS");
    }

    public static MySqlCluster initCluster(TestApplication app, Location location, EntitySpec<MySqlCluster> spec) {
        MySqlCluster mysql = app.createAndManageChild(spec);
        app.start(ImmutableList.of(location));
        log.info("MySQL started");
        return mysql;
    }

    private static void assertSlave(final VogellaExampleAccess masterDb, final VogellaExampleAccess slaveDb, final int recordCnt) throws Exception {
        Asserts.succeedsEventually(new Runnable() {
            private static final String QUERY = "SELECT C.myuser, webpage, datum, summary, COMMENTS from COMMENTS as C INNER JOIN items.INVENTORY as I ON C.MYUSER=I.MYUSER";
            @Override
            public void run() {
                try {
                    List<List<String>> masterResult = masterDb.read(QUERY);
                    assertEquals(masterResult.size(), recordCnt);
                    assertEquals(masterResult, slaveDb.read(QUERY));
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        });
    }

    public static String execSql(MySqlNode node, String cmd) {
        return node.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of("commands", cmd)).asTask().getUnchecked();
    }

}
