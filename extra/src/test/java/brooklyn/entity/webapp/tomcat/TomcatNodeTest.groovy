package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.AttributePoller

/**
 * This tests the operation of the {@link TomcatNode} entity.
 * 
 * TODO clarify test purpose
 * TODO check disabled tests
 */
class TomcatNodeTest {
    private static final Logger logger = LoggerFactory.getLogger(TomcatNodeTest.class)

//    @InheritConstructors
    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @BeforeMethod
    public void patchInSimulator() {
        TomcatNode.metaClass.startInLocation = { SimulatedLocation loc ->
            delegate.locations.add(loc)
            TomcatSimulator sim = new TomcatSimulator(loc, delegate)
            delegate.metaClass.simulator = sim
            sim.start()
        }
        TomcatNode.metaClass.shutdownInLocation { SimulatedLocation loc ->
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

    @Test
    public void ensureNodeCanStartAndShutdown() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app);
        
        try { 
            tc.start([ new SimulatedLocation() ]);
            try { tc.stop() } catch (Exception e) {
                throw new Exception("tomcat is throwing exceptions when shutting down; this will break most tests", e) }
        } catch (Exception e) {
            throw new Exception("tomcat is throwing exceptions when starting; this will break most tests", e)
        }
    }
    
    @Test
    public void ensureNodeShutdownCleansUp() {
        Application app = new TestApplication();
        TomcatNode tc1 = new TomcatNode(owner: app);
        TomcatNode tc2 = new TomcatNode(owner: app);
        
        try {
            tc1.start([ new SimulatedLocation() ]);
            tc1.stop()
        } catch (Exception e) {} //NOOP
        
        try { 
            tc2.start([ new SimulatedLocation() ])
        } catch (IllegalStateException e) {
            throw new Exception("tomcat should clean up after itself in case of failure; this will break most tests", e)
        } finally {
            try { tc2.stop() } catch (Exception e) {} //NOOP
        }
    }
    
    @Test
    public void detectEarlyDeathOfTomcatProcess() {
        Application app = new TestApplication();
        TomcatNode tc1 = new TomcatNode(owner: app);
        TomcatNode tc2 = new TomcatNode(owner: app);
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

    @Test
    public void rejectIfLocationNotSupplied() {
        Application app = new TestApplication();
        boolean caught = false
        TomcatNode tc = new TomcatNode(owner: app);
        try {
            tc.start([])
            tc.stop()
        } catch(Exception e) {
            caught = true
        }
        assertEquals(true, caught)
    }
    
    @Test
    public void ensureRequestsPerSecondIsReportedCorrectly() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app) {
            public void initJmxSensors() {
                super.initJmxSensors()
                attributePoller.removeSensor(TomcatNode.REQUEST_COUNT)
            }
        }
        
        tc.start([ new SimulatedLocation() ]);
        
        tc.emit(TomcatNode.REQUEST_COUNT, 0);
        Thread.sleep(1000);
        tc.emit(TomcatNode.REQUEST_COUNT, 10);
        Thread.sleep(1000);
        tc.emit(TomcatNode.REQUEST_COUNT, 10);
        
        Assert.assertEquals(tc.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND), 10/JavaWebApp.AVG_REQUESTS_PER_SECOND_PERIOD*1000, 0.1);
        println tc.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
    }
}
 
