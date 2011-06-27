package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*

import java.util.Map

import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.TimeExtras

/**
 * This tests the operation of the {@link TomcatNode} entity.
 */
class TomcatNodeTest {
	private static final Logger logger = LoggerFactory.getLogger(TomcatNodeTest.class)

//	@InheritConstructors
	static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @Before
    public void patchInSimulator() {
        TomcatNode.metaClass.startInLocation = { SimulatedLocation loc ->
            TomcatSimulator sim = new TomcatSimulator(loc, delegate)
            delegate.simulator = sim
            sim.start([ new SimulatedLocation() ])
        }
        TomcatNode.metaClass.shutdownInLocation { SimulatedLocation loc ->
            TomcatSimulator sim = delegate.simulator
            assertEquals loc, sim.location
            sim.shutdown()
        }
    }

    @After
    public void ensureSimulatorIsShutDownForNextTest() {
        boolean wasFree = TomcatSimulator.reset();
        if (wasFree == false)
            logger.error "TomcatSimulator was locked. If tests failed this is not unexpected. If tests passed, then this needs investigation."
    }

	@Test
    @Ignore
	public void ensureNodeCanStartAndShutdown() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(owner: app);
		
		try { 
			tc.start([ new SimulatedLocation() ]);
			try { tc.shutdown() } catch (Exception e) { 
				throw new Exception("tomcat is throwing exceptions when shutting down; this will break most tests", e) }
		} catch (Exception e) {
			throw new Exception("tomcat is throwing exceptions when starting; this will break most tests", e)
		}
	}
	
	@Test
    @Ignore
	public void ensureNodeShutdownCleansUp() {
		Application app = new TestApplication();
		TomcatNode tc1 = new TomcatNode(owner: app);
		TomcatNode tc2 = new TomcatNode(owner: app);
		
		try {
			tc1.start([ new SimulatedLocation() ]);
			tc1.shutdown()
		} catch (Exception e) {} //NOOP
		
		try { 
			tc2.start([ new SimulatedLocation() ])
		} catch (IllegalStateException e) {
			throw new Exception("tomcat should clean up after itself in case of failure; this will break most tests", e)
		} finally {
			try { tc2.shutdown() } catch (Exception e) {} //NOOP
		}
	}
	
	@Test
    @Ignore
	public void detectEarlyDeathOfTomcatProcess() {
		Application app = new TestApplication();
		TomcatNode tc1 = new TomcatNode(owner: app);
		TomcatNode tc2 = new TomcatNode(owner: app);
		tc1.start([ new SimulatedLocation() ])
		try {
			tc2.start([ new SimulatedLocation() ])
			tc2.shutdown()
			fail "should have detected that $tc2 didn't start since tomcat was already running"
		} catch (Exception e) {
			logger.debug "successfully detected failure of {} to start: {}", tc2, e.toString()
		} finally {
			tc1.shutdown()
		}
	}

	@Test
    @Ignore
	public void rejectIfLocationNotSupplied() {
		Application app = new TestApplication();
		boolean caught = false
		TomcatNode tc = new TomcatNode(owner: app);
		try {
			tc.start([ new SimulatedLocation() ])
			tc.shutdown()
		} catch(Exception e) {
			caught = true
		}
		assertEquals(true, caught)
	}
}
 
