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
package brooklyn.entity.messaging.kafka;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class KafkaLiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Kafka cluster with two brokers.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        final KafkaCluster cluster = app.createAndManageChild(EntitySpec.create(KafkaCluster.class)
                .configure("startTimeout", 300) // 5 minutes
                .configure("initialSize", 2));
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", 300000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(cluster.getAttribute(Startable.SERVICE_UP));
                assertTrue(cluster.getZooKeeper().getAttribute(Startable.SERVICE_UP));
                assertEquals(cluster.getCurrentSize().intValue(), 2);
                return null;
            }
        });

        Entities.dumpInfo(cluster);

        KafkaSupport support = new KafkaSupport(cluster);

        support.sendMessage("brooklyn", "TEST_MESSAGE");
        String message = support.getMessage("brooklyn");
        assertEquals(message, "TEST_MESSAGE");
    }

}
