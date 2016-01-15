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
package org.apache.brooklyn.entity.messaging.jms;

import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.messaging.Queue;
import org.apache.brooklyn.entity.messaging.Topic;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class JMSBrokerImpl<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends SoftwareProcessImpl implements JMSBroker<Q,T> {
    private static final Logger log = LoggerFactory.getLogger(JMSBroker.class);
    
    Collection<String> queueNames;
    Collection<String> topicNames;
    Map<String, Q> queues = Maps.newLinkedHashMap();
    Map<String, T> topics = Maps.newLinkedHashMap();

    public JMSBrokerImpl() {
    }

    @Override
    public JMSBrokerImpl configure(Map properties) {
        if (queueNames==null) queueNames = Lists.newArrayList();
        if (groovyTruth(properties.get("queue"))) queueNames.add((String) properties.remove("queue"));
        if (groovyTruth(properties.get("queues"))) queueNames.addAll((Collection<String>) properties.remove("queues"));

        if (topicNames==null) topicNames = Lists.newArrayList();
        if (groovyTruth(properties.get("topic"))) topicNames.add((String) properties.remove("topic"));
        if (groovyTruth(properties.get("topics"))) topicNames.addAll((Collection<String>) properties.remove("topics"));
        
        return (JMSBrokerImpl) super.configure(properties);
    }

    @Override
    public Collection<String> getQueueNames() {
        return queueNames;
    }
    
    @Override
    public Collection<String> getTopicNames() {
        return topicNames;
    }

    @Override
    public Map<String, Q> getQueues() {
        return queues;
    }
    
    @Override
    public Map<String, T> getTopics() {
        return topics;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setBrokerUrl();
    }

    // should be called after sensor-polling is activated etc
    @Override
    protected void postStart() {
        super.postStart();
        // stupid to do this here, but there appears to be a race where sometimes the
        // broker throws a BrokerStopped exception, even though the sensor indicates it is up
        Time.sleep(Duration.FIVE_SECONDS);
        for (String name : queueNames) {
            addQueue(name);
        }
        for (String name : topicNames) {
            addTopic(name);
        }
    }
    
    @Override
    public abstract void setBrokerUrl();

    @Override
    public void preStop() {
        // If can't delete queues, continue trying to stop.
        // (e.g. in CI have seen activemq "BrokerStoppedException" thrown in queue.destroy()). 
        try {
            for (JMSDestination queue : queues.values()) {
                queue.destroy();
            }
        } catch (Exception e) {
            log.warn("Error deleting queues from broker "+this+"; continuing with stop...", e);
        }
        
        try {
            for (JMSDestination topic : topics.values()) {
                topic.destroy();
            }
        } catch (Exception e) {
            log.warn("Error deleting topics from broker "+this+"; continuing with stop...", e);
        }
        
        super.preStop();
    }
    
    @Override
    public void addQueue(String name) {
        addQueue(name, MutableMap.of());
    }
    
    public void checkStartingOrRunning() {
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (getAttribute(SERVICE_STATE_ACTUAL) == Lifecycle.RUNNING) return;
        if (getAttribute(SERVICE_STATE_ACTUAL) == Lifecycle.STARTING) return;
        // TODO this check may be redundant or even inappropriate
        throw new IllegalStateException("Cannot run against "+this+" in state "+state);
    }

    @Override
    public void addQueue(String name, Map properties) {
        checkStartingOrRunning();
        properties.put("name", name);
        queues.put(name, createQueue(properties));
    }

    @Override
    public abstract Q createQueue(Map properties);

    @Override
    public void addTopic(String name) {
        addTopic(name, MutableMap.of());
    }
    
    @Override
    public void addTopic(String name, Map properties) {
        checkStartingOrRunning();
        properties.put("name", name);
        topics.put(name, createTopic(properties));
    }

    @Override
    public abstract T createTopic(Map properties);
}
