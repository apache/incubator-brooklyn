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
package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * Cassandra integration tests.
 *
 * Test the operation of the {@link CassandraNode} class.
 */
public class CassandraNodeIntegrationTest extends AbstractCassandraNodeTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("jmxPort", "11099+")
                .configure("rmiRegistryPort", "19001+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);
        Entities.dumpInfo(app);

        cassandra.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a keyspace and column family can be created and used with Astyanax client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("jmxPort", "11099+")
                .configure("rmiRegistryPort", "19001+")
                .configure("thriftPort", "9876+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);

        AstyanaxSample astyanax = new AstyanaxSample(cassandra);
        astyanax.astyanaxTest();
    }
}
