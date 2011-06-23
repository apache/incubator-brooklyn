package brooklyn.entity.webapp.tomcat

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
import static brooklyn.test.TestUtils.isPortInUse

/**
 * This tests the operation of the {@link TomcatNode} entity.
 */
class TomcatNodeTest {
	private static final Logger logger = LoggerFactory.getLogger(TomcatNodeTest.class)

	/** don't use 8080 since that is commonly used by testing software */
	static int DEFAULT_HTTP_PORT = 7880
	
//	@InheritConstructors
	static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

	static boolean httpPortLeftOpen = false;

	@Before
	public void fail_if_http_port_in_use() {
		if (isPortInUse(DEFAULT_HTTP_PORT)) {
			httpPortLeftOpen = true;
			fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
		}
	}
	@After
	//can't fail because that swallows the original exception, grrr!
	public void moan_if_http_port_in_use() {
		if (!httpPortLeftOpen && isPortInUse(DEFAULT_HTTP_PORT, 1000))
			logger.warn "port $DEFAULT_HTTP_PORT still running after test"
	}
	private int oldHttpPort=-1;
	@Before
	public void changeDefaultHttpPort() {
		oldHttpPort = Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT;
		Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT = DEFAULT_HTTP_PORT
	}
	@After
	public void changeDefaultHttpPortBack() {
		if (oldHttpPort>0)
			Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT = oldHttpPort
	}
	
    @Before
    public void patchInSimulator() {
        TomcatNode.metaClass.startInLocation = { SimulatedLocation loc ->
            TomcatSimulator sim = new TomcatSimulator(loc, delegate)
            delegate.simulator = sim
            sim.start()
        }
        TomcatNode.metaClass.shutdownInLocation { SimulatedLocation loc ->
            TomcatSimulator sim = delegate.simulator
            assertEquals loc, sim.location
            sim.shutdown()
        }
    }

	@Test
	public void ensureNodeCanStartAndShutdown() {
		Application app = new TestApplication(httpPort: DEFAULT_HTTP_PORT);
		TomcatNode tc = new TomcatNode(owner: app);
		
		try { 
			tc.start(location: new SimulatedLocation());
			try { tc.shutdown() } catch (Exception e) { 
				throw new Exception("tomcat is throwing exceptions when shutting down; this will break most tests", e) }
		} catch (Exception e) {
			throw new Exception("tomcat is throwing exceptions when starting; this will break most tests", e)
		}
	}
	
	@Test
	public void ensureNodeShutdownCleansUp() {
		Application app = new TestApplication(httpPort: DEFAULT_HTTP_PORT);
		TomcatNode tc1 = new TomcatNode(owner: app);
		TomcatNode tc2 = new TomcatNode(owner: app);
		
		try {
			tc1.start(location: new SimulatedLocation());
			tc1.shutdown()
		} catch (Exception e) {} //NOOP
		
		try { 
			tc2.start(location: new SimulatedLocation())
		} catch (IllegalStateException e) {
			throw new Exception("tomcat should clean up after itself in case of failure; this will break most tests", e)
		} finally {
			try { tc2.shutdown() } catch (Exception e) {} //NOOP
		}
	}
	
	@Test
	public void detectEarlyDeathOfTomcatProcess() {
		Application app = new TestApplication(httpPort: DEFAULT_HTTP_PORT);
		TomcatNode tc1 = new TomcatNode(owner: app);
		TomcatNode tc2 = new TomcatNode(owner: app);
		tc1.start(location: new SimulatedLocation())
		try {
			tc2.start(location: new SimulatedLocation())
			tc2.shutdown()
			fail "should have detected that $tc2 didn't start since tomcat was already running"
		} catch (Exception e) {
			logger.debug "successfully detected failure of {} to start: {}", tc2, e.toString()
		} finally {
			tc1.shutdown()
		}
	}

	@Test
	public void acceptsLocationAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(owner:app);
		tc.start(location: new SimulatedLocation())
		tc.shutdown()
	}

	@Test
	public void acceptsLocationInEntity() {
		logger.debug ""
		Application app = new TestApplication(location:new SimulatedLocation());
		TomcatNode tc = [ owner: app ]
		tc.start()
		tc.shutdown()
	}
	
	@Test
	public void acceptsEntityLocationSameAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = [ owner:app, location:new SimulatedLocation() ]
		tc.start(location: new SimulatedLocation())
		tc.shutdown()
	}
	
	@Test
	public void rejectIfEntityLocationConflictsWithStartParameter() {
		Application app = new TestApplication()
		boolean caught = false
		TomcatNode tc = [ owner:app, location:new SshMachineLocation(name:'tokyo', host:'localhost') ]
		try {
			tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
			tc.shutdown()
		} catch(Exception e) {
			caught = true
		}
		assertEquals(true, caught)
	}
	
	@Test
	public void rejectIfLocationNotInEntityOrInStartParameter() {
		Application app = new TestApplication();
		boolean caught = false
		TomcatNode tc = new TomcatNode(owner: app);
		try {
			tc.start()
			tc.shutdown()
		} catch(Exception e) {
			caught = true
		}
		assertEquals(true, caught)
	}
}
 
