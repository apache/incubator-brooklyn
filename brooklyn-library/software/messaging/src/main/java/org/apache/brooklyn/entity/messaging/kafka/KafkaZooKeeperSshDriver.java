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

import java.util.Map;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;

import static org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes.escapeLiteralForDoubleQuotedBash;

public class KafkaZooKeeperSshDriver extends AbstractfKafkaSshDriver implements KafkaZooKeeperDriver {

    public KafkaZooKeeperSshDriver(KafkaZooKeeperImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("zookeeperPort", getZookeeperPort());
    }

    @Override
    protected ConfigKey<String> getConfigTemplateKey() {
        return KafkaZooKeeper.KAFKA_ZOOKEEPER_CONFIG_TEMPLATE;
    }

    @Override
    protected String getConfigFileName() {
        return "zookeeper.properties";
    }

    @Override
    protected String getLaunchScriptName() {
        return "zookeeper-server-start.sh";
    }

    @Override
    protected String getTopicsScriptName() {
        return "kafka-topics.sh";
    }

    @Override
    protected String getProcessIdentifier() {
        return "quorum\\.QuorumPeerMain";
    }

    @Override
    public Integer getZookeeperPort() {
        return getEntity().getAttribute(KafkaZooKeeper.ZOOKEEPER_PORT);
    }

    @Override
    public void createTopic(String topic) {
        String zookeeperUrl = getEntity().getAttribute(Attributes.HOSTNAME) + ":" + getZookeeperPort();
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(String.format("./bin/%s  --create --zookeeper \"%s\" --replication-factor 1 --partitions 1 --topic \"%s\"",
                                           getTopicsScriptName(),
                                           escapeLiteralForDoubleQuotedBash(zookeeperUrl),
                                           escapeLiteralForDoubleQuotedBash(topic)))
                .execute();
    }
}
