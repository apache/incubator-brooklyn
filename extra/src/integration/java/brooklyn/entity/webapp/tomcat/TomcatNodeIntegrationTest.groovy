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
class TomcatNodeIntegrationTest {
	private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatNodeIntegrationTest.class)

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
	public void publishes_requests_per_second_metric() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(owner: app);
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
				def activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals Integer, activityValue.class
				if (activityValue==-1) return new BooleanWithMessage(false, "activity not set yet (-1)")
				
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
        tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
        executeUntilSucceedsWithShutdown(tc, {
            def port = tc.getAttribute(TomcatNode.HTTP_PORT)
            // Connect to non-existent URL n times
            def n = 5
            def connection = n.times { connectToURL("http://localhost:${port}/does_not_exist") }
            int errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
            logger.info "$errorCount errors in total"
            
            // TODO firm up assertions.  confused by the values returned (generally n*2?)
            assert errorCount > 0
            assertEquals 0, errorCount % n
        })
    }
	
	@Test
	public void deploy_web_app_appears_at_URL() {
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
			} catch(EntityStartException e) {
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
    
    /**
     * Connects to the given url and returns the connection.
     * @param u
     * @return
     */
    private URLConnection connectToURL(String u) {
        URL url = [u]
        URLConnection connection = url.openConnection()
        connection.connect()
        connection.getContentLength() // Make sure the connection is made.
        return connection
    }
    
    /** convenience for entities to ensure they shutdown afterwards */
    public static void executeUntilSucceedsWithShutdown(Map flags=[:], Entity entity, Runnable r) {
        executeUntilSucceedsWithFinallyBlock(flags, r, { entity.shutdown() })
    }

	/**
	 * Convenience method for cases where we need to test until something is true.
	 * The runnable will be invoked periodically until it succesfully concludes.
	 * Additionally, a finally block can be supplied.
	 * 
	 * @param flags, accepts boolean abortOnError (default true), abortOnException (default false), 
	 * useGroovyTruth (defaults to false; any result code apart from 'false' will be treated as success including null; ignored for Runnables which aren't Callables), 
	 * timeout (a TimeDuration, defaults to 30*SECONDS), period (a TimeDuration, defaults to 500*MILLISECONDS),
	 * maxAttempts (integer, Integer.MAX_VALUE)
	 * @param entity
	 * @param r
	 */
	public static void executeUntilSucceedsWithFinallyBlock(Map flags=[:], Runnable r, Runnable finallyBlock={}) {
		println "abortOnError = "+flags.abortOnError
		boolean abortOnException = flags.abortOnException ?: false
		boolean abortOnError = flags.abortOnError ?: true
		boolean useGroovyTruth = flags.useGroovyTruth ?: false
		TimeDuration timeout = flags.timeout ?: 30*SECONDS
		TimeDuration period = flags.period ?: 500*MILLISECONDS
		int maxAttempts = flags.maxAttempts ?: Integer.MAX_VALUE
		try {
			Throwable lastException = null;
			Object result;
			long lastAttemptTime = 0;
			long startTime = System.currentTimeMillis()
			long expireTime = startTime+timeout.toMilliseconds()
			int attempt = 0;
			while (attempt<maxAttempts && lastAttemptTime<expireTime) {
				try {
					attempt++
					lastAttemptTime = System.currentTimeMillis()
					if (r in Callable) {
						result = r.call();
						logger.trace "Attempt ${attempt} after ${System.currentTimeMillis()-startTime}ms: ${result}"
						if (useGroovyTruth) {
							if (result) return;
						} else if (result!=false) return;
					} else {
						r.run()
						return
					}
					lastException = null
				} catch(Throwable e) {
					lastException = e
					logger.trace "Attempt $attempt after ${System.currentTimeMillis()-startTime}ms: ${e.message}"
					if (abortOnException) throw e
					if (abortOnError && e in Error) throw e
				}
				if (period.toMilliseconds()>0) Thread.sleep period.toMilliseconds()
			}
			logger.trace "Exceeded max attempts or timeout - $attempt attempts lasting {}ms", System.currentTimeMillis()-startTime
			if (lastException != null)
				throw lastException
			fail "invalid result code $result"
		} finally {
			finallyBlock.run()
		}
	}

	public static class BooleanWithMessage {
		boolean value; String message;
		public BooleanWithMessage(boolean value, String message) {
			this.value = value; this.message = message;
		}
		public boolean asBoolean() { return value }
		public String toString() { return message }
	}
	
}
 