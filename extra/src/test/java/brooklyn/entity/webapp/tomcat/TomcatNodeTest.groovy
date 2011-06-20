package brooklyn.entity.webapp.tomcat

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*
import groovy.time.TimeDuration

import java.util.Map
import java.util.concurrent.Callable

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EntityStartException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.TimeExtras
import org.junit.Ignore

/**
 * This tests the operation of the {@link TomcatNode} entity.
 */
class TomcatNodeTest {
	private static final Logger logger = LoggerFactory.getLogger(TomcatNodeTest.class)

	static {
        TimeExtras.init()
    }
	
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

	public boolean isPortInUse(int port, long retryAfterMillis=0) {
		try {
			def s = new Socket("localhost", port)
			s.close()
			if (retryAfterMillis>0) {
				logger.debug "port $port still open, waiting 1s for it to really close"
				//give it 1s to close
				Thread.sleep retryAfterMillis
				s = new Socket("localhost", port)
				s.close()
			}
			logger.debug "port $port still open (conclusive)"
			return true
		} catch (ConnectException e) {
			return false
		}
	}

	@Test
	public void acceptsLocationAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent:app);
		tc.start(location: new SimulatedLocation())
		tc.shutdown()
	}

	@Test
	public void acceptsLocationInEntity() {
		logger.debug ""
		Application app = new TestApplication(location:new SimulatedLocation());
		TomcatNode tc = [ parent: app ]
		tc.start()
		tc.shutdown()
	}
	
	@Test
	public void acceptsEntityLocationSameAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = [ parent:app, location:new SimulatedLocation() ]
		tc.start(location: new SimulatedLocation())
		tc.shutdown()
	}
	
	@Test
	public void rejectIfEntityLocationConflictsWithStartParameter() {
		Application app = new TestApplication()
		boolean caught = false
		TomcatNode tc = [ parent:app, location:new SshMachineLocation(name:'tokyo', host:'localhost') ]
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
		TomcatNode tc = new TomcatNode(parent: app);
		try {
			tc.start()
			tc.shutdown()
		} catch(Exception e) {
			caught = true
		}
		assertEquals(true, caught)
	}

	@Test
	public void detectEarlyDeathOfTomcatProcess() {
        Application app = new TestApplication(httpPort: DEFAULT_HTTP_PORT);
        TomcatNode tc1 = new TomcatNode(parent: app);
        TomcatNode tc2 = new TomcatNode(parent: app);
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
}
 