package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*

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
	
    @Test
    public void canStartupAndShutdown() {
        JBossNode jb = new JBossNode(owner:app, portIncrement: portIncrement);
		jb.start(location: testLocation)
		assert (new JBoss6SshSetup(jb)).isRunning(testLocation)
        jb.shutdown()
		assert ! (new JBoss6SshSetup(jb)).isRunning(testLocation)
    }
	
	@Test
	public void canAlterPortIncrement() {
		int pI = 1020
		int httpPort = baseHttpPort + pI
		JBossNode jb = new JBossNode(owner:app, portIncrement: pI);
		// Assert httpPort is contactable.
		logger.info "Starting JBoss with HTTP port $httpPort"
		jb.start(location: testLocation)
		
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
	
	// Failing @Test
	public void publishesRequestsPerSecondMetric() {
		JBossNode jb = new JBossNode(owner:app);
		jb.start(location: testLocation)
		executeUntilSucceedsWithShutdown(jb, {
			
            // Connect to non-existent URL n times
            def n = 5
			def url = "http://localhost:${httpPort}/does_not_exist"
			println url
            def connection = n.times {
				try {
					 connectToURL(url) 
				} catch (Exception e) {
					println e
				}
            }
            int errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
			jb.updateJmxSensors()	
            println "$errorCount errors in total"
            
            // TODO firm up assertions.  confused by the values returned (generally n*2?)
            assert errorCount > 0
            assertEquals 0, errorCount % n
			
		}, timeout: 6*SECONDS, useGroovyTruth: true)
		
	}

}
