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

import java.io.File;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

/**
 * Runs a slightly modified version of the popular Vogella MySQL tutorial,
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class MySqlIntegrationTest extends BrooklynAppLiveTestSupport {

    public static final Logger log = LoggerFactory.getLogger(MySqlIntegrationTest.class);
    
    // can start in AWS by running this -- or use brooklyn CLI/REST for most clouds, or programmatic/config for set of fixed IP machines
    static String hostname = Networking.getLocalHost().getHostName();

    // From http://www.vogella.de/articles/MySQLJava/article.html
    // Expects COMMENTS to be injected as the test.table.name config value, for VogellaExampleAccess to work.
    public static final String CREATION_SCRIPT = Joiner.on("\n").join(ImmutableList.of(
            "CREATE DATABASE feedback;",
            "CREATE USER 'sqluser'@'localhost' IDENTIFIED BY 'sqluserpw';",
            "GRANT USAGE ON *.* TO 'sqluser'@'localhost';",
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'localhost';",
            "CREATE USER 'sqluser'@'%' IDENTIFIED BY 'sqluserpw';",
            "GRANT USAGE ON *.* TO 'sqluser'@'%';",
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'%';",
            "CREATE USER 'sqluser'@'$hostname' IDENTIFIED BY 'sqluserpw';",
            "GRANT USAGE ON *.* TO 'sqluser'@'$hostname';",
            "GRANT ALL PRIVILEGES ON feedback.* TO 'sqluser'@'$hostname';",
            "FLUSH PRIVILEGES;",
            "USE feedback;",
            "CREATE TABLE ${config['test.table.name']} (",
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
            "INSERT INTO ${config['test.table.name']} values (default, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );"
            ));

    @Test(groups = {"Integration"})
    public void test_localhost() throws Exception {
        File dataDir = Files.createTempDir();
        try {
            MySqlNode mysql = app.createAndManageChild(EntitySpec.create(MySqlNode.class)
                    .configure("mysql.server.conf", MutableMap.of("skip-name-resolve",""))
                    .configure("creationScriptContents", CREATION_SCRIPT)
                    .configure("dataDir", dataDir.getAbsolutePath())
                    .configure("test.table.name", "COMMENTS")); // to ensure creation script is templated
            LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
            
            app.start(ImmutableList.of(location));;
            log.info("MySQL started");
    
            new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(MySqlNode.DATASTORE_URL)).readModifyAndRevertDataBase();
    
            log.info("Ran vogella MySQL example -- SUCCESS");
    
            // Ensure the data directory was successfully overridden.
            File mysqlSubdirFile = new File(dataDir, "mysql");
            Assert.assertTrue(mysqlSubdirFile.exists());
        } finally {
            Os.deleteRecursively(dataDir);
        }
    }
}
