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

import java.util.List;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.entity.database.VogellaExampleAccess;

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
            "INSERT INTO COMMENTS values (default, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );"
            ));

    public static void test(TestApplication app, Location location) throws Exception {
        MySqlCluster mysql = app.createAndManageChild(EntitySpec.create(MySqlCluster.class)
                .configure(MySqlCluster.INITIAL_SIZE, 2)
                .configure(MySqlNode.MYSQL_SERVER_CONF, MutableMap.<String, Object>of("skip-name-resolve","")));

        app.start(ImmutableList.of(location));
        log.info("MySQL started");
        MySqlNode masterEntity = (MySqlNode) mysql.getAttribute(MySqlCluster.FIRST);
        masterEntity.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of("commands", CREATION_SCRIPT)).asTask().getUnchecked();

        VogellaExampleAccess masterDb = new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(MySqlNode.DATASTORE_URL));
        VogellaExampleAccess slaveDb = new VogellaExampleAccess("com.mysql.jdbc.Driver", Iterables.getOnlyElement(mysql.getAttribute(MySqlCluster.SLAVE_DATASTORE_URL_LIST)));
        masterDb.connect();
        slaveDb.connect();

        assertSlave(masterDb, slaveDb, 1);
        masterDb.modifyDataBase();
        assertSlave(masterDb, slaveDb, 2);
        masterDb.revertDatabase();
        assertSlave(masterDb, slaveDb, 1);

        masterDb.close();
        slaveDb.close();

        log.info("Ran vogella MySQL example -- SUCCESS");
    }

    private static void assertSlave(final VogellaExampleAccess masterDb, final VogellaExampleAccess slaveDb, final int recordCnt) throws Exception {
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                try {
                    List<List<String>> masterResult = masterDb.readDataBase();
                    assertEquals(masterResult.size(), recordCnt);
                    assertEquals(masterResult, slaveDb.readDataBase());
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        });
    }
}
