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
package brooklyn.entity.database.mariadb;

import java.io.File;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

/**
 * Runs a slightly modified version of the popular Vogella MySQL tutorial,
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class MariaDbIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(MariaDbIntegrationTest.class);
    
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext managementContext;
    protected TestApplication tapp;
    protected String hostname;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        // can start in AWS by running this -- or use brooklyn CLI/REST for most clouds, or programmatic/config for set of fixed IP machines
        hostname = InetAddress.getLocalHost().getHostName();

        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        tapp = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void ensureShutDown() throws Exception {
        Entities.destroyAllCatching(managementContext);
    }

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT =
            "CREATE DATABASE feedback; " +
            "CREATE USER 'sqluser'@'localhost' IDENTIFIED BY 'sqluserpw'; " +
            "GRANT USAGE ON *.* TO 'sqluser'@'localhost'; " +
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'localhost'; " +
            "CREATE USER 'sqluser'@'%' IDENTIFIED BY 'sqluserpw'; " +
            "GRANT USAGE ON *.* TO 'sqluser'@'%'; " +
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'%'; " +
            "CREATE USER 'sqluser'@'$hostname' IDENTIFIED BY 'sqluserpw'; " +
            "GRANT USAGE ON *.* TO 'sqluser'@'$hostname'; " +
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'$hostname'; " +
            "FLUSH PRIVILEGES; " +
            "USE feedback; " +
            "CREATE TABLE COMMENTS ( " +
                    "id INT NOT NULL AUTO_INCREMENT,  " +
                    "MYUSER VARCHAR(30) NOT NULL, " +
                    "EMAIL VARCHAR(30),  " +
                    "WEBPAGE VARCHAR(100) NOT NULL,  " +
                    "DATUM DATE NOT NULL,  " +
                    "SUMMARY VARCHAR(40) NOT NULL, " +
                    "COMMENTS VARCHAR(400) NOT NULL, " +
                    "PRIMARY KEY (ID) " +
                "); " +
            "INSERT INTO COMMENTS values (default, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );";

    @Test(groups = "Integration")
    public void test_localhost() throws Exception {
        String dataDir = "/tmp/mariadb-data-" + Strings.makeRandomId(8);
        MariaDbNode mariadb = tapp.createAndManageChild(EntitySpec.create(MariaDbNode.class)
                .configure(MariaDbNode.MARIADB_SERVER_CONF, MutableMap.<String, Object>of("skip-name-resolve",""))
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(MariaDbNode.DATA_DIR, dataDir));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();

        tapp.start(ImmutableList.of(location));
        log.info("MariaDB started");

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mariadb.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();

        log.info("Ran vogella MySQL example -- SUCCESS");

        // Ensure the data directory was successfully overridden.
        File dataDirFile = new File(dataDir);
        File mariadbSubdirFile = new File(dataDirFile, "mysql");
        Assert.assertTrue(mariadbSubdirFile.exists());

        // Clean up.
        dataDirFile.delete();
    }
}
