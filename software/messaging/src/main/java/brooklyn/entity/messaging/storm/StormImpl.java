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
package brooklyn.entity.messaging.storm;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.entity.java.JavaSoftwareProcessDriver;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

public class StormImpl extends SoftwareProcessImpl implements Storm {

    private static final Logger log = LoggerFactory.getLogger(StormImpl.class);

    public static final ObjectName STORM_MBEAN = JmxHelper.createObjectName("backtype.storm.daemon.nimbus:type=*");

    private JmxHelper jmxHelper;
    private volatile JmxFeed jmxFeed;
    
    public StormImpl() {}
    
    @Override
    public String getHostname() { return getAttribute(HOSTNAME); }

    @Override
    public Role getRole() { return getConfig(ROLE); }

    @Override
    public String getStormConfigTemplateUrl() { return getConfig(STORM_CONFIG_TEMPLATE_URL); }   
    
    @Override
    public Class<?> getDriverInterface() {
        return StormDriver.class;
    }

    public String getRoleName() { return getRole().name().toLowerCase(); }

    @Override
    protected void preStart() {
        setDefaultDisplayName("Storm Node ("+ getRoleName()+")");
        super.preStart();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        // give it plenty of time to start before we advertise ourselves
        Time.sleep(Duration.TEN_SECONDS);

        if (getRole() == Role.UI) {
            setAttribute(STORM_UI_URL, "http://"+getAttribute(Attributes.HOSTNAME)+":"+getAttribute(UI_PORT)+"/");
        }

        if (((JavaSoftwareProcessDriver)getDriver()).isJmxEnabled()) {
            jmxHelper = new JmxHelper(this);
//            jmxFeed = JmxFeed.builder()
//                    .entity(this)
//                    .period(3000, TimeUnit.MILLISECONDS)
//                    .helper(jmxHelper)
//                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP_JMX)
//                            .objectName(STORM_MBEAN)
//                            .attributeName("Initialized")
//                            .onSuccess(Functions.forPredicate(Predicates.notNull()))
//                            .onException(Functions.constant(false)))
//                    // TODO SERVICE_UP should really be a combo of JMX plus is running
//                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
//                            .objectName(STORM_MBEAN)
//                            .attributeName("Initialized")
//                            .onSuccess(Functions.forPredicate(Predicates.notNull()))
//                            .onException(Functions.constant(false)))
//                    .build();
            jmxFeed = JavaAppUtils.connectMXBeanSensors(this);
            
            // FIXME for now we do service up based on pid check -- we get a warning that:
            // JMX object backtype.storm.daemon.nimbus:type=* not found at service:jmx:jmxmp://108.59.82.105:31001
            // (JMX is up fine, but no such object there)
            connectServiceUpIsRunning();
         } else {
            // if not using JMX
            log.warn("Storm running without JMX monitoring; limited visibility of service available");
            connectServiceUpIsRunning();
        }
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
        if (jmxHelper !=null) jmxHelper.terminate();
    }

}
