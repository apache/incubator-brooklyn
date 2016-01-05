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

import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.entity.software.base.AbstractDockerLiveTest;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.testng.annotations.Test;

public class MysqlDockerLiveTest extends AbstractDockerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
       MySqlNode mysql = app.createAndManageChild(EntitySpec.create(MySqlNode.class)
               .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MySqlIntegrationTest.CREATION_SCRIPT)
               .configure("test.table.name", "COMMENTS"));

       app.start(ImmutableList.of(loc));

       new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(DatastoreCommon.DATASTORE_URL))
               .readModifyAndRevertDataBase();
    }

    @Test(enabled=false)
    public void testDummy() { } // Convince testng IDE integration that this really does have test methods
}

