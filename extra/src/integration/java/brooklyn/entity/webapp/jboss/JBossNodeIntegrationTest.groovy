package brooklyn.entity.webapp.jboss;

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*
import static brooklyn.test.TestUtils.*

import org.junit.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation

class JBossNodeTest {

	private static final int HTTP_PORT = 8080

	static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }
	
    @Test
    public void canStartupAndShutdown() {
        Application app = new TestApplication();
        JBossNode jb = new JBossNode(owner:app);
        Location loc = new SshMachineLocation(name:'london', host:'localhost')
		jb.start(location: loc)
		assert (new JBoss6SshSetup(jb)).isRunning(loc)
        jb.shutdown()
    }
	
	// Failing @Test
	public void publishesRequestsPerSecondMetric() {
		Application app = new TestApplication();
		JBossNode jb = new JBossNode(owner:app);
		jb.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(jb, {
			
            // Connect to non-existent URL n times
            def n = 5
			def url = "http://localhost:${HTTP_PORT}/does_not_exist"
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
