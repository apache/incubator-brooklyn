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
package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.java.JavaAppUtils;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.time.Duration;

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

    private volatile JmxFeed jmxFeed;

    @Override
    public void connectSensors() {
        super.connectSensors();

        if (getDriver().isJmxEnabled()) {
            String requestProcessorMbeanName = "Catalina:type=GlobalRequestProcessor,name=\"http-*\"";
            String connectorMbeanName = format("Catalina:type=Connector,port=%s", getAttribute(HTTP_PORT));

            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .period(500, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("errorCount"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("requestCount"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("processingTime"))
                    .pollAttribute(new JmxAttributePollConfig<String>(CONNECTOR_STATUS)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName"))
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName")
                            .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo("STARTED")))
                            .setOnFailureOrException(false))
                    .build();

            JavaAppUtils.connectMXBeanSensors(this);
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
           if (jmxFeed != null) jmxFeed.stop();
        } else {
            disconnectServiceUpIsRunning();
        }
    }

    @Override
    public Class getDriverInterface() {
        return Tomcat7Driver.class;
    }
    
    @Override
    public String getShortName() {
        return "Tomcat";
    }
}

