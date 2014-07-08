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

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import brooklyn.entity.database.postgresql.PostgreSqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;

public class RubyRepEc2LiveTest extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        PostgreSqlNode db1 = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, PortRanges.fromInteger(9111)));

        PostgreSqlNode db2 = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, PortRanges.fromInteger(9111)));

        RubyRepIntegrationTest.startInLocation(app, db1, db2, loc);
        RubyRepIntegrationTest.testReplication(db1, db2);
    }

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Debian_6() throws Exception { } // Disabled because PostgreSql 9.1 not available

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Ubuntu_10_0() throws Exception { } // Disabled because PostgreSql 9.1 not available

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Debian_7_2() throws Exception { } // Diabling all except Ubuntu 12.0 temporarily

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_CentOS_6_3() throws Exception { } // Diabling all except Ubuntu 12.0 temporarily

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_CentOS_5_6() throws Exception { } // Diabling all except Ubuntu 12.0 temporarily

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception { } // Diabling all except Ubuntu 12.0 temporarily

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}

