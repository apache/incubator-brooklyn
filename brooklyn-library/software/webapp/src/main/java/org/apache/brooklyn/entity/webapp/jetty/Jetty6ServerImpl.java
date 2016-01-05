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
package org.apache.brooklyn.entity.webapp.jetty;

import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.java.JavaAppUtils;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import org.apache.brooklyn.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.feed.jmx.JmxFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Jetty instance.
 */
public class Jetty6ServerImpl extends JavaWebAppSoftwareProcessImpl implements Jetty6Server {

    private static final Logger log = LoggerFactory.getLogger(Jetty6ServerImpl.class);

    private volatile JmxFeed jmxFeedJetty, jmxFeedMx;

    @Override
    public void connectSensors() {
        super.connectSensors();
        
        if (getDriver().isJmxEnabled()) {
            String serverMbeanName = "org.mortbay.jetty:type=server,id=0";
            String statsMbeanName = "org.mortbay.jetty.handler:type=atomicstatisticshandler,id=0";

            jmxFeedJetty = JmxFeed.builder()
                    .entity(this)
                    .period(500, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                            .objectName(serverMbeanName)
                            .attributeName("running")
                            .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo(true)))
                            .setOnFailureOrException(false))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("requests")
                            .onFailureOrException(EntityFunctions.attribute(this, REQUEST_COUNT)))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(RESPONSES_4XX_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("responses4xx"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(RESPONSES_5XX_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("responses5xx"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                            .objectName(statsMbeanName)
                            .attributeName("requestTimeTotal"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(MAX_PROCESSING_TIME)
                            .objectName(statsMbeanName)
                            .attributeName("requestTimeMax"))
                    // NB: requestsActive may be useful
                    .build();
            
            enrichers().add(Enrichers.builder()
                    .combining(RESPONSES_4XX_COUNT, RESPONSES_5XX_COUNT)
                    .publishing(ERROR_COUNT)
                    .computingSum()
                    .build());

            jmxFeedMx = JavaAppUtils.connectMXBeanSensors(this);
        } else {
            // if not using JMX
            log.warn("Jetty running without JMX monitoring; limited visibility of service available");
            // TODO we could do simple things, like check that web server is accepting connections
        }
    }

    @Override
    protected void disconnectSensors() {
        if (jmxFeedJetty != null) jmxFeedJetty.stop();
        if (jmxFeedMx != null) jmxFeedMx.stop();
        super.disconnectSensors();
    }

    public Integer getJmxPort() {
        if (((Jetty6Driver) getDriver()).isJmxEnabled()) {
            return getAttribute(UsesJmx.JMX_PORT);
        } else {
            return Integer.valueOf(-1);
        }
    }

    @Override
    public Class getDriverInterface() {
        return Jetty6Driver.class;
    }
    
    @Override
    public String getShortName() {
        return "Jetty";
    }
    
    @Override
    public void deploy(String url, String targetName) {
        super.deploy(url, targetName);
        restartIfRunning();
    }

    @Override
    public void undeploy(String targetName) {
        super.undeploy(targetName);
        restartIfRunning();
    }
    
    protected void restartIfRunning() {
        // TODO for now we simply restart jetty to achieve "hot deployment"; should use the config mechanisms
        Lifecycle serviceState = getAttribute(SERVICE_STATE_ACTUAL);
        if (serviceState == Lifecycle.RUNNING)
            restart();
        // may need a restart also if deploy effector is done in parallel to starting
        // but note this routine is used by initialDeployWars so just being in starting state is not enough!
    }

}

