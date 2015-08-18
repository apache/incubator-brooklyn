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

import org.apache.brooklyn.core.util.flags.SetFromFlag;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/**
 * Shared Kafka broker and zookeeper properties.
 */
public interface Kafka {

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.9.2-0.8.2.1");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.DOWNLOAD_URL, "http://apache.cbox.biz/kafka/0.8.2.1/kafka_${version}.tgz");

    // TODO: Upgrade to version 0.8.0, which will require refactoring of the sensors to reflect the changes to the JMX beans
//    @SetFromFlag("downloadUrl")
//    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
//            Attributes.DOWNLOAD_URL, "http://mirror.catn.com/pub/apache/kafka/${version}/kafka-${version}-src.tgz");

}
