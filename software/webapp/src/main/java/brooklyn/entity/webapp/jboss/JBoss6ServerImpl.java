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
package brooklyn.entity.webapp.jboss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;

import com.google.common.base.Functions;

public class JBoss6ServerImpl extends JavaWebAppSoftwareProcessImpl implements JBoss6Server {

    public static final Logger log = LoggerFactory.getLogger(JBoss6ServerImpl.class);

    private volatile JmxFeed jmxFeed;
    
    public JBoss6ServerImpl() {
        this(new LinkedHashMap(), null);
    }

    public JBoss6ServerImpl(Entity parent) {
        this(new LinkedHashMap(), parent);
    }

    public JBoss6ServerImpl(Map flags){
        this(flags, null);
    }

    public JBoss6ServerImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        String requestProcessorMbeanName = "jboss.web:type=GlobalRequestProcessor,name=http-*";
        String serverMbeanName = "jboss.system:type=Server";
        
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
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(serverMbeanName)
                        .attributeName("Started")
                        .onException(Functions.constant(false)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (jmxFeed != null) jmxFeed.stop();
    }
    
    @Override
    public Class<JBoss6Driver> getDriverInterface() {
        return JBoss6Driver.class;
    }
}
