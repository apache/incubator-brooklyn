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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class CassandraNodeEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        log.info("Testing Cassandra on {}", loc);

        CassandraNode cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("thriftPort", "9876+")
                .configure("clusterName", "TestCluster"));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, CassandraNode.SERVICE_UP, true);

        AstyanaxSample astyanax = new AstyanaxSample(cassandra);
        astyanax.astyanaxTest();
    }
}
