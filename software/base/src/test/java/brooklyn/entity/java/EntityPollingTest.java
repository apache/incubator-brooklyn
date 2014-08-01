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
package brooklyn.entity.java;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.JmxService;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EntityPollingTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntityPollingTest.class);

    private JmxService jmxService;
    private TestApplication app;
    private SoftwareProcess entity;
    
    private static final BasicAttributeSensor<String> stringAttribute = new BasicAttributeSensor<String>(
            String.class, "brooklyn.test.stringAttribute", "Brooklyn testing int attribute");
    private String objectName = "Brooklyn:type=MyTestMBean,name=myname";
    
    private static final ObjectName jmxObjectName;
    static {
        try {
            jmxObjectName = new ObjectName("Brooklyn:type=MyTestMBean,name=myname");
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    private static final String attributeName = "myattrib";

    public static class SubVanillaJavaApp extends VanillaJavaAppImpl {
        private JmxFeed feed;
        
        @Override protected void connectSensors() {
            super.connectSensors();
   
            // Add a sensor that we can explicitly set in jmx
            feed = JmxFeed.builder()
                .entity(this)
                .pollAttribute(new JmxAttributePollConfig<String>(stringAttribute)
                    .objectName(jmxObjectName)
                    .attributeName(attributeName))
                .build();
        }

        @Override
        public void disconnectSensors() {
            super.disconnectSensors();
            if (feed != null) feed.stop();
        }
        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Class getDriverInterface() {
            return null;
        }

        @Override
        public VanillaJavaAppSshDriver newDriver(MachineLocation loc) {
            return new VanillaJavaAppSshDriver(this, (SshMachineLocation)loc) {
                @Override public void install() {
                    // no-op
                }
                @Override public void customize() {
                    // no-op
                }
                @Override public void launch() {
                    // no-op
                }
                @Override public boolean isRunning() {
                    return true;
                }
                @Override public void stop() {
                    // no-op
                }
                @Override public void kill() {
                    // no-op
                }
            };
        }
    };        

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        
        /*
         * Create an entity, using real entity code, but that swaps out the external process
         * for a JmxService that we can control in the test.        
         */
        entity = app.createAndManageChild(EntitySpec.create(SoftwareProcess.class).impl(SubVanillaJavaApp.class)
                .configure("rmiRegistryPort", 40123)
                .configure("mxbeanStatsEnabled", false)
                .configure(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (jmxService != null) jmxService.shutdown();
    }

	// Tests that the happy path works
    @Test(groups="Integration")
    public void testSimpleConnection() throws Exception {
        jmxService = new JmxService("localhost", 40123);
        jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);

        app.start(ImmutableList.of(new SshMachineLocation(MutableMap.of("address", "localhost"))));
        
        // Starts with value defined when registering...
        EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval");
    }

	// Test that connect will keep retrying (e.g. start script returns before the JMX server is up)
    @Test(groups="Integration")
    public void testEntityWithDelayedJmxStartupWillKeepRetrying() {
		// In 2 seconds time, we'll start the JMX server
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    jmxService = new JmxService("localhost", 40123);
                    jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);
                } catch (Exception e) {
                    LOG.error("Error in testEntityWithDelayedJmxStartupWillKeepRetrying", e);
                    throw Exceptions.propagate(e);
                }
            }});

        try {
            t.start();
            app.start(ImmutableList.of(new SshMachineLocation(MutableMap.of("address", "localhost"))));

            EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval");
            
        } finally {
            t.interrupt();
        }
    }
    
    @Test(groups="Integration")
    public void testJmxConnectionGoesDownRequiringReconnect() throws Exception {
        jmxService = new JmxService("localhost", 40123);
        jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);

        app.start(ImmutableList.of(new SshMachineLocation(MutableMap.of("address", "localhost"))));
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval");
        
        // Shutdown the MBeanServer - simulates network failure so can't connect
        jmxService.shutdown();
        
        // TODO Want a better way of determining that the entity is down; ideally should have 
		// sensor for entity-down that's wired up to a JMX attribute?
        Thread.sleep(5000);

        // Restart MBeanServer, and set attribute to different value; expect it to be polled again
        jmxService = new JmxService("localhost", 40123);
        jmxService.registerMBean(ImmutableMap.of(attributeName, "myval2"), objectName);
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval2");
    }
}
