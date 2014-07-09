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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class KafkaBrokerSshDriver extends AbstractfKafkaSshDriver implements KafkaBrokerDriver {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBrokerSshDriver.class);

    public KafkaBrokerSshDriver(KafkaBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("kafkaPort", getKafkaPort());
    }

    @Override
    protected ConfigKey<String> getConfigTemplateKey() {
        return KafkaBroker.KAFKA_BROKER_CONFIG_TEMPLATE;
    }

    @Override
    protected String getConfigFileName() {
        return "server.properties";
    }

    @Override
    protected String getLaunchScriptName() {
        return "kafka-server-start.sh";
    }

    @Override
    protected String getProcessIdentifier() {
        return "kafka\\.Kafka";
    }

    @Override
    public Integer getKafkaPort() {
        return getEntity().getAttribute(KafkaBroker.KAFKA_PORT);
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        JmxAgentModes jmxAgentMode = getEntity().getConfig(KafkaBroker.JMX_AGENT_MODE);
        String jmxPort;
        if (jmxAgentMode == JmxAgentModes.NONE) {
            // seems odd to pass RMI port here, as it gets assigned to com.sun.mgmt.jmx.port in kafka-run-class.sh
            // but RMI server/registry port works, whereas JMX port does not
            jmxPort = String.valueOf(entity.getAttribute(UsesJmx.JMX_PORT));
        } else {
            /*
             * See ./bin/kafka-server-start.sh  and ./bin/kafka-run-class.sh
             * Really hard to turn off jmxremote on kafka! And can't use default because
             * uses 9999, which means could only run one kafka broker per server.
             */
            jmxPort = String.valueOf(entity.getAttribute(KafkaBroker.INTERNAL_JMX_PORT));
        }
        
        return MutableMap.<String, String> builder()
                .putAll(super.getShellEnvironment())
                .put("JMX_PORT", jmxPort)
                .build();
    }
}
