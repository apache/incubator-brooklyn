package brooklyn.test

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*
import groovy.time.TimeDuration

import java.net.URLConnection
import java.util.Map
import java.util.concurrent.Callable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.util.internal.TimeExtras

/**
 * Helper functions for tests of Tomcat, JBoss and others.
 */
class TestUtils {
	private static final Logger logger = LoggerFactory.getLogger(brooklyn.test.TestUtils.class)

	static {
		TimeExtras.init()
	}

	/** True if two attempts to connect to the port succeed. */
	public static boolean isPortInUse(int port, long retryAfterMillis=0) {
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

	/** Connects to the given url and returns the connection. */
	public static URLConnection connectToURL(String u) {
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
		public boolean asBoolean() {
			return value
		}
		public String toString() {
			return message
		}
	}
}
