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
package org.apache.brooklyn.entity.nosql.couchdb;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.nosql.couchdb.CouchDBNode;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * CouchDB integration tests.
 *
 * Test the operation of the {@link CouchDBNode} class.
 */
public class CouchDBNodeIntegrationTest extends AbstractCouchDBNodeTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdown() {
        couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        couchdb.stop();

        EntityTestUtils.assertAttributeEquals(couchdb, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a node can be used with jcouchdb client.
     */
    @Test(groups = {"Integration", "WIP"})
    public void testConnection() throws Exception {
        couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdb = new JcouchdbSupport(couchdb);
        jcouchdb.jcouchdbTest();
    }
}
