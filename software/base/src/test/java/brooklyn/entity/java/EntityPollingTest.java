package brooklyn.entity.java;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.GeneralisedDynamicMBean;
import brooklyn.test.JmxService;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EntityPollingTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntityPollingTest.class);

    private static final int TIMEOUT_MS = 5000;
    private static final int SHORT_WAIT = 250;
    
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
    private static final String opName = "myop";

    public static class SubVanillaJavaApp extends VanillaJavaApp {
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
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        
        /*
         * Create an entity, using real entity code, but that swaps out the external process
         * for a JmxService that we can control in the test.        
         */
        entity = app.createAndManageChild(EntitySpecs.spec(SoftwareProcess.class).impl(SubVanillaJavaApp.class)
                .configure("jmxPort", 40123)
                .configure("jmxContext", null)
                .configure("mxbeanStatsEnabled", false));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (jmxService != null) jmxService.shutdown();
    }

	// Tests that the happy path works
    @Test(groups="Integration")
    public void testSimpleConnection() {
        jmxService = new JmxService("localhost", 40123);
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);

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
                    GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);
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
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(attributeName, "myval"), objectName);

        app.start(ImmutableList.of(new SshMachineLocation(MutableMap.of("address", "localhost"))));
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval");
        
        // Shutdown the MBeanServer - simulates network failure so can't connect
        jmxService.shutdown();
        
        // TODO Want a better way of determining that the entity is down; ideally should have 
		// sensor for entity-down that's wired up to a JMX attribute?
        Thread.sleep(5000);

        // Restart MBeanServer, and set attribute to different value; expect it to be polled again
        jmxService = new JmxService("localhost", 40123);
        GeneralisedDynamicMBean mbean2 = jmxService.registerMBean(ImmutableMap.of(attributeName, "myval2"), objectName);
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, stringAttribute, "myval2");
    }
}
