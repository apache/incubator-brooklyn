/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.zookeeper.AbstractZookeeperImpl;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
 */
public class KafkaZookeeperImpl extends AbstractZookeeperImpl implements KafkaZookeeper {

    private static final Logger log = LoggerFactory.getLogger(KafkaZookeeperImpl.class);

    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = Kafka.DOWNLOAD_URL;

    public KafkaZookeeperImpl() {
    }

    @Override
    public Class<?> getDriverInterface() {
        return KafkaZookeeperDriver.class;
    }

}
