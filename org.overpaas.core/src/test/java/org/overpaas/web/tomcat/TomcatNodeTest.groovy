package org.overpaas.web.tomcat;

import static org.junit.Assert.*
import groovy.time.TimeDuration;
import groovy.transform.InheritConstructors

import java.util.Map
import java.util.concurrent.Callable;
import static java.util.concurrent.TimeUnit.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test
import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Application
import org.overpaas.entities.Entity;
import org.overpaas.locations.SshMachineLocation
import org.overpaas.util.TimeExtras;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TomcatNodeTest {

	private static final Logger logger = LoggerFactory.getLogger(TomcatNode.class);

	static { TimeExtras.init() }
	
	/** don't use 8080 since that is commonly used by testing software */
	static int DEFAULT_HTTP_PORT = 7880
	
	@InheritConstructors
	static class TestApplication extends AbstractApplication {}

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
		oldHttpPort = TomcatNode.Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT;
		TomcatNode.Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT = DEFAULT_HTTP_PORT
	}
	@After
	public void changeDefaultHttpPortBack() {
		if (oldHttpPort>0)
			TomcatNode.Tomcat7SshSetup.DEFAULT_FIRST_HTTP_PORT = oldHttpPort
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
		tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
		tc.shutdown()
	}
	
	@Test
	public void acceptsLocationInEntity() {
		logger.debug ""
		Application app = new TestApplication(location:new SshMachineLocation(name:'london', host:'localhost'));
		TomcatNode tc = [ parent: app ]
		tc.start()
		tc.shutdown()
	}
	
	@Test
	public void acceptsEntityLocationSameAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = [ parent:app, location:new SshMachineLocation(name:'london', host:'localhost') ]
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
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
	public void fails_if_doesnt_actually_start() {
		TomcatNode tc1, tc2;
		try {
			Application app = new TestApplication(httpPort: DEFAULT_HTTP_PORT);
			tc1 = new TomcatNode(parent: app);
			tc2 = new TomcatNode(parent: app);
			tc1.start(location: new SshMachineLocation(name:'london', host:'localhost'))
			try {
				tc2.start(location: new SshMachineLocation(name:'london', host:'localhost'))
				fail "should have detected that $tc2 didn't start since port $DEFAULT_HTTP_PORT was in use"
			} catch (Exception e) {
				logger.debug "successfully detected failure of {} to start: {}", tc2, e.toString()
			}
		} finally {
			if (tc1) tc1.shutdown();
			if (tc2) tc2.shutdown();
		}
	} 
		
	@Test
	public void publishes_requests_per_second_metric() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent: app);
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
				def activityValue = tc.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals Integer, activityValue.class
				if (activityValue==-1) return new BooleanWithMessage(false, "activity not set yet (-1)")
				
				assertEquals 0, activityValue
				
				def port = tc.activity.getValue(TomcatNode.HTTP_PORT)
				URL url = [ "http://localhost:${port}/foo" ]
				URLConnection connection = url.openConnection()
				connection.connect()
				assertEquals "Apache-Coyote/1.1", connection.getHeaderField("Server")

				Thread.sleep 1000
				activityValue = tc.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals 1, activityValue
				true
			}, timeout: 10*SECONDS, useGroovyTruth: true)
	}
	
	@Test
	public void deploy_web_app_appears_at_URL() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent: app);
		tc.war = "resources/hello-world.war"
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
				def port = tc.activity.getValue(TomcatNode.HTTP_PORT)
				URL url = [ "http://localhost:${port}/hello-world" ]
				URLConnection connection = url.openConnection()
				connection.connect()
				int status = ((HttpURLConnection)connection).getResponseCode()
				logger.info "connection to {} gives {}", url, status
				assertEquals 200, status
				//return the following, for fun&interest
				tc.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
			}, abortOnError: false)
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
		boolean abortOnException = valueOrDefault flags.abortOnException, false
		boolean abortOnError = valueOrDefault flags.abortOnError, true
		boolean useGroovyTruth = valueOrDefault flags.useGroovyTruth, false
		TimeDuration timeout = valueOrDefault flags.timeout, 30*SECONDS
		TimeDuration period = valueOrDefault flags.period, 500*MILLISECONDS
		int maxAttempts = valueOrDefault flags.maxAttempts, Integer.MAX_VALUE
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
	
	private static Object valueOrDefault(Object v, Object fallback) {
		v!=null ? v : fallback
	}
}
