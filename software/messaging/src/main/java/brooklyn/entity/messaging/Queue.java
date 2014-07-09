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
package brooklyn.entity.messaging;

import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;

/**
 * An interface that describes a messaging queue.
 */
public interface Queue {
    BasicAttributeSensorAndConfigKey<String> QUEUE_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "queue.name", "Queue name");

    AttributeSensor<Integer> QUEUE_DEPTH_BYTES = Sensors.newIntegerSensor("queue.depth.bytes", "Queue depth in bytes");
    AttributeSensor<Integer> QUEUE_DEPTH_MESSAGES = Sensors.newIntegerSensor("queue.depth.messages", "Queue depth in messages");
    
    /**
     * Create the queue.
     *
     * TODO make this an effector
     */
    abstract void create();

    /**
     * Delete the queue.
     *
     * TODO make this an effector
     */
    abstract void delete();

    String getQueueName();

}
