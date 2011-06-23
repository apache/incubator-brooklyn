package brooklyn.entity.webapp.tomcat

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*
import static brooklyn.test.TestUtils.*

import groovy.time.TimeDuration

import java.util.Map

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EntityStartException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.TimeExtras

/**
 * This tests the operation of the {@link TomcatNode} entity.
 */
class TomcatNodeIntegrationTest {
	
	private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatNodeIntegrationTest.class)
	
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

    @Test
    public void tracksNodeState() {
        TomcatNode tc = [ 
            owner: new TestApplication(), 
            location:new SshMachineLocation(name:'london', host:'localhost')
        ]
        tc.start()
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue tc.getAttribute(TomcatNode.NODE_UP)
        }, {
            tc.shutdown()
        })
    }
    
	@Test
	public void publishesRequestsPerSecondMetric() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(owner: app);
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
				def activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
				if (activityValue == null || activityValue == -1) return new BooleanWithMessage(false, "activity not set yet ($activityValue)")
                
				assertEquals Integer, activityValue.class
				assertEquals 0, activityValue
				
				def port = tc.getAttribute(TomcatNode.HTTP_PORT)
                def connection = connectToURL "http://localhost:${port}/foo"
				assertEquals "Apache-Coyote/1.1", connection.getHeaderField("Server")

				Thread.sleep 1000
				activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals 1, activityValue
				true
			}, timeout:10*SECONDS, useGroovyTruth:true)
	}
    
    @Test
    public void publishesErrorCountMetric() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app);
        tc.start(location:new SshMachineLocation(name:'london', host:'localhost'))
        executeUntilSucceedsWithShutdown(tc, {
            def port = tc.getAttribute(TomcatNode.HTTP_PORT)
            def errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
			if (errorCount == null) return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
 
            // Connect to non-existent URL n times
            def n = 5
            n.times { connectToURL("http://localhost:${port}/does_not_exist") }
            errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
            logger.info "$errorCount errors in total"
            
            // TODO firm up assertions.  confused by the values returned (generally n*2?)
            assertTrue errorCount > 0
            assertEquals 0, errorCount % n
        })
    }
	
	@Test
	public void deployWebAppAppearsAtUrl() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(owner: app);

        URL resource = this.getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull resource
        tc.war = resource.getPath()

		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
            def port = tc.getAttribute(TomcatNode.HTTP_PORT)
            def url  = "http://localhost:${port}/hello-world"
            def connection = connectToURL(url)
            int status = ((HttpURLConnection)connection).getResponseCode()
            logger.info "connection to {} gives {}", url, status
            if (status == 404)
                throw new Exception("App is not there yet (404)");
            assertEquals 200, status
        }, abortOnError: false)
	}

	
	@Test
	public void detect_failure_if_tomcat_cant_bind_to_port() {
		ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
		Thread t = new Thread({ try { for(;;) { Socket socket = listener.accept(); socket.close(); } } catch(Exception e) {} })
		t.start()
		try {
			Application app = new TestApplication()
			TomcatNode tc = new TomcatNode(owner:app)
			Exception caught = null
			try {
                tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
			} catch (EntityStartException e) {
				caught = e
			} finally {
				tc.shutdown()
			}
			assertNotNull caught
			assertFalse tc.getAttribute(TomcatNode.NODE_UP)
			logger.debug "The exception that was thrown was:", caught
		} finally {
			listener.close();
			t.join();
		}
	}
    

	
}
 
