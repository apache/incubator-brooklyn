package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.webapp.OldJavaWebApp
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * TODO clarify test purpose
 * TODO check disabled tests
 */
class TomcatServerTest {
    private static final Logger logger = LoggerFactory.getLogger(TomcatServerTest.class)

    /*
     * FIXME Tests are misbehaving, with an warning ike:
     *     JMX management can't find MBean Catalina:type=GlobalRequestProcessor,name="http-*" (using service:jmx:rmi:///jndi/rmi://localhost:28120/jmxrmi)
     * 
     * If I change TomcatServer to remove the quotes around "http-*", then this test passes. But
     * the integration test fails, so the quotes are unfortunately needed.
     * 
     * It looks like our brooklyn.test.JmxService isn't behaving right, nor is the Tomcat JMX service. 
     * http://docs.oracle.com/javase/6/docs/api/javax/management/ObjectName.html says that it should 
     * accept the value with and without quotes. 
     * 
     * Testing in the debugger, the following works:
     *     mbsc.queryMBeans(new javax.management.ObjectName("Catalina:type=GlobalRequestProcessor,name=*"), null)
     * but with \"*\" then it fails. So it's a pretty low-level problem.
     * 
     * The tests do eventually pass, but it takes 90 seconds or 180 seconds for some!
     */
    
    @BeforeMethod
    public void patchInSimulator() {
        TomcatServer.metaClass.startInLocation = { SimulatedLocation loc ->
            delegate.locations.add(loc)
            TomcatSimulator sim = new TomcatSimulator(loc, delegate)
            delegate.metaClass.simulator = sim
            sim.start()
        }
		def oldInitJmxSensors = TomcatServer.metaClass.getMetaMethod("initJmxSensors", [] as Class[]);
		TomcatServer.metaClass.initJmxSensors = {
			if (delegate.locations && delegate.locations.iterator().next() in SimulatedLocation) {
				logger.info "skipping JMX for simulated $this"
			} else {
				oldInitJmxSensors.call()
			}
        }
        TomcatServer.metaClass.stopInLocation = { SimulatedLocation loc ->
            TomcatSimulator sim = delegate.simulator
            assertEquals loc, sim.location
            sim.shutdown()
        }
    }

    @AfterMethod
    public void ensureSimulatorIsShutDownForNextTest() {
        boolean wasFree = TomcatSimulator.reset();
        if (wasFree == false)
            logger.error "TomcatSimulator was locked. If tests failed this is not unexpected. If tests passed, then this needs investigation."
    }

    // FIXME temporarily disabled by aledsage - 20120518
    @Test(enabled = false)
    public void ensureNodeCanStartAndShutdown() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner: app);
        
        tc.start([ new SimulatedLocation() ]);
        tc.stop() 
    }
    
    // FIXME temporarily disabled by aledsage - 20120518
    @Test(enabled = false, dependsOnMethods = [ "ensureNodeCanStartAndShutdown" ])
    public void ensureNodeShutdownCleansUp() {
        Application app = new TestApplication();
        TomcatServer tc1 = new TomcatServer(owner: app);
        TomcatServer tc2 = new TomcatServer(owner: app);
        
        try {
            tc1.start([ new SimulatedLocation() ]);
            tc1.stop()
        } catch (Exception e) {} //NOOP
        
        try { 
            tc2.start([ new SimulatedLocation() ])
        } catch (IllegalStateException e) {
            throw new Exception("tomcat did not cleanup after itself on stop", e)
        } finally {
            try { tc2.stop() } catch (Exception e) {} //NOOP
        }
    }
    
    // FIXME temporarily disabled by aledsage - 20120518
    @Test(enabled = false, dependsOnMethods = [ "ensureNodeCanStartAndShutdown" ])
    public void detectEarlyDeathOfTomcatProcess() {
        Application app = new TestApplication();
        TomcatServer tc1 = new TomcatServer(owner: app);
        TomcatServer tc2 = new TomcatServer(owner: app);
        tc1.start([ new SimulatedLocation() ])
        try {
            tc2.start([ new SimulatedLocation() ])
            tc2.stop()
            fail "should have detected that $tc2 didn't start since tomcat was already running"
        } catch (Exception e) {
            logger.debug "successfully detected failure of {} to start: {}", tc2, e.toString()
        } finally {
            tc1.stop()
        }
    }

    // FIXME temporarily disabled by aledsage - 20120518
    @Test(enabled = false, dependsOnMethods = [ "ensureNodeCanStartAndShutdown" ])
    public void rejectIfLocationNotSupplied() {
        Application app = new TestApplication();
        boolean caught = false
        TomcatServer tc = new TomcatServer(owner: app);
        try {
            tc.start([])
            tc.stop()
        } catch(Exception e) {
            caught = true
        }
        assertEquals(true, caught)
    }
    
    // FIXME temporarily disabled by grkvlt - 20110923
    @Test(enabled = false, dependsOnMethods = [ "ensureNodeCanStartAndShutdown" ])
    public void ensureRequestsPerSecondIsReportedCorrectly() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner: app) {
            @Override
            public void addJmxSensors() {
                super.addJmxSensors()
                sensorRegistry.removeSensor(TomcatServer.REQUEST_COUNT)
            }
        }
        
        tc.start([ new SimulatedLocation() ]);

        tc.emit(TomcatServer.REQUEST_COUNT, 0);
        Thread.sleep(1000)
        tc.emit(TomcatServer.REQUEST_COUNT, 10);
        Thread.sleep(1000)
        tc.emit(TomcatServer.REQUEST_COUNT, 10);
        Thread.sleep(1000)
        
        assertEquals tc.getAttribute(OldJavaWebApp.AVG_REQUESTS_PER_SECOND).value, 10/OldJavaWebApp.AVG_REQUESTS_PER_SECOND_PERIOD*1000, 0.2d
    }
}
 
