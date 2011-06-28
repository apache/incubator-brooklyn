package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation

class JBossNodeIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

	static int baseHttpPort = 8080
	static int portIncrement = 300
	static int httpPort = baseHttpPort + portIncrement

	private Application app
	private Location testLocation

	static class TestApplication extends AbstractApplication {
		public TestApplication(Map properties=[:]) {
			super(properties)
		}
	}

	@Before
	public void setup() {
		app = new TestApplication();
		testLocation = new SshMachineLocation(name:'london', host:'localhost')
	}

	@Before
	public void fail_if_http_port_in_use() {
		if (isPortInUse(httpPort)) {
			fail "someone is already listening on port $httpPort; tests assume that port $httpPort is free on localhost"
		}
	}

	@After
	public void waitForShutdown() {
		logger.info "Sleeping for shutdown"
		Thread.sleep 4000
	}

	@Test
	public void canStartupAndShutdown() {
		JBossNode jb = new JBossNode(owner:app, portIncrement: portIncrement);
		jb.start([testLocation])
		assert (new JBoss6SshSetup(jb, testLocation)).isRunning(testLocation)
		jb.shutdown()
		// Potential for JBoss to be in process of shutting down here..
		Thread.sleep 4000
		assert ! (new JBoss6SshSetup(jb, testLocation)).isRunning(testLocation)
	}

	@Test
	public void canAlterPortIncrement() {
		int pI = 1020
		int httpPort = baseHttpPort + pI
		JBossNode jb = new JBossNode(owner:app, portIncrement: pI);
		// Assert httpPort is contactable.
		logger.info "Starting JBoss with HTTP port $httpPort"
		jb.start([testLocation])

		executeUntilSucceedsWithShutdown(jb, {
			def url = "http://localhost:$httpPort"
			def connection = connectToURL(url)
			int status = ((HttpURLConnection)connection).getResponseCode()
			logger.info "connection to {} gives {}", url, status
			if (status == 404)
				throw new Exception("App is not there yet (404)");
			assertEquals 200, status
		}, abortOnError: false)
	}

	@Test
	public void publishesRequestsPerSecondMetric() {
		JBossNode jb = new JBossNode(owner:app, portIncrement: portIncrement);
		jb.start([testLocation])
		executeUntilSucceedsWithShutdown(jb, {
			def errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
			if (errorCount == null) return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")

			// Connect to non-existent URL n times
			def n = 5
			def url = "http://localhost:${httpPort}/does_not_exist"
			println url
			n.times {
				def connection = connectToURL(url)
				int status = ((HttpURLConnection)connection).getResponseCode()
				logger.info "connection to {} gives {}", url, status
			}
			Thread.sleep(1000L)
			errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
			println "$errorCount errors in total"

			// TODO firm up assertions.  confused by the values returned (generally n*2?)
			assertTrue errorCount > 0
			assertEquals 0, errorCount % n
			true
		}, abortOnError: false, timeout:10*SECONDS, useGroovyTruth:true)

	}

}
