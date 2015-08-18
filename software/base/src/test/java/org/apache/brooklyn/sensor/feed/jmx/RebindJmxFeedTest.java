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
package org.apache.brooklyn.sensor.feed.jmx;

import static org.testng.Assert.assertEquals;

import java.util.Collection;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.core.Attributes;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.java.UsesJmx.JmxAgentModes;
import org.apache.brooklyn.entity.software.base.test.jmx.GeneralisedDynamicMBean;
import org.apache.brooklyn.entity.software.base.test.jmx.JmxService;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.feed.ConfigToAttributes;
import org.apache.brooklyn.sensor.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.sensor.feed.jmx.JmxFeed;
import org.apache.brooklyn.sensor.feed.jmx.JmxHelper;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.core.PortRanges;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class RebindJmxFeedTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindJmxFeedTest.class);

    private static final String LOCALHOST_NAME = "localhost";

    static final AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    static final AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

    static final String JMX_ATTRIBUTE_NAME = "myattr";
    static final String OBJECT_NAME = "Brooklyn:type=MyTestMBean,name=myname";
    
    private JmxService jmxService;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        // Create an entity and configure it with the above JMX service
        //jmxService = newJmxServiceRetrying(LOCALHOST_NAME, 5);
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (jmxService != null) jmxService.shutdown();
        super.tearDown();
    }

    @Test
    public void testJmxFeedIsPersisted() throws Exception {
        runJmxFeedIsPersisted(false);
    }

    @Test
    public void testJmxFeedIsPersistedWithPreCreatedJmxHelper() throws Exception {
        runJmxFeedIsPersisted(true);
    }

    protected void runJmxFeedIsPersisted(boolean preCreateJmxHelper) throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithJmxFeedImpl.class)
                .configure(MyEntityWithJmxFeedImpl.PRE_CREATE_JMX_HELPER, preCreateJmxHelper));
        origApp.start(ImmutableList.<Location>of());
        
        jmxService = new JmxService(origEntity);
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(MutableMap.of(JMX_ATTRIBUTE_NAME, "myval"), OBJECT_NAME);
        
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_STRING, "myval");
        assertEquals(origEntity.feeds().getFeeds().size(), 1);

        newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_STRING, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_STRING, "myval");
    }

    public static class MyEntityWithJmxFeedImpl extends TestEntityImpl {
        public static final ConfigKey<Boolean> PRE_CREATE_JMX_HELPER = ConfigKeys.newBooleanConfigKey("test.rebindjmx.preCreateJmxHelper", "", false);
        
        @Override
        public void start(Collection<? extends Location> locs) {
            // TODO Auto-generated method stub
            super.start(locs);
            
            setAttribute(Attributes.HOSTNAME, "localhost");
            setAttribute(UsesJmx.JMX_PORT, 
                    LocalhostMachineProvisioningLocation.obtainPort(PortRanges.fromString("40123+")));
            // only supports no-agent, at the moment
            setConfig(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.NONE);
            setAttribute(UsesJmx.RMI_REGISTRY_PORT, -1);  // -1 means to use the JMX_PORT only
            ConfigToAttributes.apply(this, UsesJmx.JMX_CONTEXT);
            
            JmxFeed.Builder feedBuilder = JmxFeed.builder()
                    .entity(this)
                    .pollAttribute(new JmxAttributePollConfig<String>(SENSOR_STRING)
                            .objectName(OBJECT_NAME)
                            .period(50)
                            .attributeName(JMX_ATTRIBUTE_NAME));
            if (getConfig(PRE_CREATE_JMX_HELPER)) {
                JmxHelper jmxHelper = new JmxHelper(this);
                feedBuilder.helper(jmxHelper);
            }
            addFeed(feedBuilder.build());
        }
    }
}
