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
package org.apache.brooklyn.entity.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.entity.zookeeper.AbstractZooKeeperImpl;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Kafka zookeeper instance.
 */
public class KafkaZooKeeperImpl extends AbstractZooKeeperImpl implements KafkaZooKeeper {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(KafkaZooKeeperImpl.class);

    public KafkaZooKeeperImpl() {
    }

    @Override
    public Class<?> getDriverInterface() {
        return KafkaZooKeeperDriver.class;
    }

    @Override
    public void createTopic(String topic) {
        ((KafkaZooKeeperDriver)getDriver()).createTopic(topic);
    }
}
