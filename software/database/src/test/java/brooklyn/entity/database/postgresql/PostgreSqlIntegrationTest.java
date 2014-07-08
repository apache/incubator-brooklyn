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
package brooklyn.entity.database.postgresql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.google.common.collect.ImmutableList;

/**
 * Runs the popular Vogella MySQL tutorial against PostgreSQL
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class PostgreSqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlIntegrationTest.class);
    
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext managementContext;
    protected TestApplication tapp;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        tapp = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun = true)
    public void ensureShutDown() {
        Entities.destroyAllCatching(managementContext);
    }

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT =
            "CREATE USER sqluser WITH PASSWORD 'sqluserpw';\n" +
            "CREATE DATABASE feedback OWNER sqluser;\n" +
            "\\c feedback;\n" +
            "CREATE TABLE COMMENTS ( " +
                    "id INT8 NOT NULL,  " +
                    "MYUSER VARCHAR(30) NOT NULL, " +
                    "EMAIL VARCHAR(30),  " +
                    "WEBPAGE VARCHAR(100) NOT NULL,  " +
                    "DATUM DATE NOT NULL,  " +
                    "SUMMARY VARCHAR(40) NOT NULL, " +
                    "COMMENTS VARCHAR(400) NOT NULL, " +
                    "PRIMARY KEY (ID) " +
                ");\n" +
            "GRANT ALL ON comments TO sqluser;\n" +
            "INSERT INTO COMMENTS values (1, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );";

    @Test(groups = "Integration")
    public void test_localhost() throws Exception {
        PostgreSqlNode pgsql = tapp.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB")); // Very low so kernel configuration not needed

        tapp.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        String url = pgsql.getAttribute(DatastoreCommon.DATASTORE_URL);
        log.info("PostgreSql started on "+url);
        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
        log.info("Ran vogella PostgreSql example -- SUCCESS");
    }
}
