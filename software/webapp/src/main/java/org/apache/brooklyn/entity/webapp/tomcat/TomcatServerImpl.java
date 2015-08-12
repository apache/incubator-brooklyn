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
package org.apache.brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;

import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.java.JavaAppUtils;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServerImpl extends JavaWebAppSoftwareProcessImpl implements TomcatServer {

    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerImpl.class);

    public TomcatServerImpl() {
        super();
    }

    private volatile JmxFeed jmxWebFeed;
    private volatile JmxFeed jmxAppFeed;

    @Override
    public void connectSensors() {
        super.connectSensors();

        if (getDriver().isJmxEnabled()) {
            String requestProcessorMbeanName = "Catalina:type=GlobalRequestProcessor,name=\"http-*\"";

            Integer port = isHttpsEnabled() ? getAttribute(HTTPS_PORT) : getAttribute(HTTP_PORT);
            String connectorMbeanName = format("Catalina:type=Connector,port=%s", port);
            boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);

            jmxWebFeed = JmxFeed.builder()
                    .entity(this)
                    .period(3000, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_PROCESS_IS_RUNNING)
                            // TODO Want to use something different from SERVICE_PROCESS_IS_RUNNING,
                            // to indicate this is jmx MBean's reported state (or failure to connect)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName")
                            .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo("STARTED")))
                            .setOnFailureOrException(false)
                            .suppressDuplicates(true))
                    .pollAttribute(new JmxAttributePollConfig<String>(CONNECTOR_STATUS)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName")
                            .suppressDuplicates(true))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("errorCount")
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("requestCount")
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("processingTime")
                            .enabled(retrieveUsageMetrics))
                    .build();

            jmxAppFeed = JavaAppUtils.connectMXBeanSensors(this);
        } else {
            // if not using JMX
            LOG.warn("Tomcat running without JMX monitoring; limited visibility of service available");
            connectServiceUpIsRunning();
        }
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (getDriver() != null && getDriver().isJmxEnabled()) {
           if (jmxWebFeed != null) jmxWebFeed.stop();
           if (jmxAppFeed != null) jmxAppFeed.stop();
        } else {
            disconnectServiceUpIsRunning();
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return TomcatDriver.class;
    }
    
    @Override
    public String getShortName() {
        return "Tomcat";
    }
}

