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
package brooklyn.entity.messaging.rabbit;

import brooklyn.entity.messaging.Queue;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class RabbitQueue extends RabbitDestination implements Queue {

    private SshFeed sshFeed;

    public RabbitQueue() {
    }
    
    public String getName() {
        return getDisplayName();
    }

    @Override
    public void create() {
        setAttribute(QUEUE_NAME, getName());
        super.create();
    }

    @Override
    protected void connectSensors() {
        String runDir = getParent().getRunDir();
        String cmd = String.format("%s/sbin/rabbitmqctl list_queues -p /%s  | grep '%s'", runDir, getVirtualHost(), getQueueName());
        
        sshFeed = SshFeed.builder()
                .entity(this)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(QUEUE_DEPTH_BYTES)
                        .env(shellEnvironment)
                        .command(cmd)
                        .onFailure(Functions.constant(-1))
                        .onSuccess(new Function<SshPollValue, Integer>() {
                                @Override public Integer apply(SshPollValue input) {
                                    return 0; // TODO parse out queue depth from output
                                }}))
                .poll(new SshPollConfig<Integer>(QUEUE_DEPTH_MESSAGES)
                        .env(shellEnvironment)
                        .command(cmd)
                        .onFailure(Functions.constant(-1))
                        .onSuccess(new Function<SshPollValue, Integer>() {
                                @Override public Integer apply(SshPollValue input) {
                                    return 0; // TODO parse out queue depth from output
                                }}))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        if (sshFeed != null) sshFeed.stop();
        super.disconnectSensors();
    }
    
    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() {
        return getName();
    }
}
