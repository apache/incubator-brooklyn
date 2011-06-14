package org.overpaas.web.tomcat;

import static org.junit.Assert.*
import groovy.transform.InheritConstructors

import java.util.Map

import org.junit.Test
import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Application
import org.overpaas.locations.SshMachineLocation
import org.overpaas.types.EntityStartException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TomcatNodeTest {

	private static final Logger logger = LoggerFactory.getLogger(TomcatNode.class)

	@InheritConstructors
	class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties);
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
		Application app = new TestApplication(location:new SshMachineLocation(name:'london', host:'localhost'));
		TomcatNode tc = new TomcatNode(parent: app);
		tc.start()
		tc.shutdown()
	}
	
	@Test
	public void acceptsEntityLocationSameAsStartParameter() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent:app, location:new SshMachineLocation(name:'london', host:'localhost'));
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		tc.shutdown()
	}
	
	@Test
	public void rejectIfEntityLocationConflictsWithStartParameter() {
		Application app = new TestApplication()
		boolean caught = false
		TomcatNode tc = new TomcatNode(parent:app, location:new SshMachineLocation(name:'tokyo', host:'localhost'))
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
	public void publishes_requests_per_second_metric() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent: app);
		tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
		Thread.sleep 2500 // TODO this should not be necessary, but REQUESTS_PER_SECOND returns -1 until things have warmed up
		executeUntilSucceedsWithShutdown(tc, {
				def activityValue = tc.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals Integer, activityValue.class
				assertEquals 0, activityValue
				
				def port = tc.activity.getValue(TomcatNode.HTTP_PORT)
				URL url = new URL("http://localhost:${port}/foo")
				URLConnection connection = url.openConnection()
				connection.connect()
				assertEquals "Apache-Coyote/1.1", connection.getHeaderField("Server")

				Thread.sleep 1000
				activityValue = tc.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
				assertEquals 1, activityValue
			})
	}
	
	@Test
	public void deploy_web_app_appears_at_URL() {
		Application app = new TestApplication();
		TomcatNode tc = new TomcatNode(parent: app);
		tc.war = "resources/hello-world.war"
		tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
		executeUntilSucceedsWithShutdown(tc, {
				def port = tc.activity.getValue(TomcatNode.HTTP_PORT)
				URL url = new URL("http://localhost:${port}/hello-world")
				URLConnection connection = url.openConnection()
				connection.connect()
				int status = ((HttpURLConnection)connection).getResponseCode()
				if (status == 404)
					throw new Exception("App is not there yet (404)");
				assertEquals 200, status
			})
	}
	
	@Test
	public void detect_failure_if_tomcat_cant_bind_to_port() {
		ServerSocket listener = new ServerSocket(8080);
		Thread t = new Thread({ try { for(;;) { Socket socket = listener.accept(); socket.close(); } } catch(Exception e) {} })
		t.start()
		try {
			Application app = new TestApplication()
			TomcatNode tc = new TomcatNode(parent: app)
			Exception caught = null
			try {
				tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
			} catch(EntityStartException e) {
				caught = e
			} finally {
				tc.shutdown()
				assertNotNull caught
				logger.debug "The exception that was thrown was:", caught
			}
		} finally {
			listener.close();
			t.join();
		}
	}
	
	/**
	 * Convenience method for cases where something takes several seconds to start up and throws exceptions if used too
	 * early. The closure is run once a second for up to 30 seconds until it completes without throwing an Exception.
	 * Additionally, a finally block will ensure that the given entity is shut down.
	 * @param entity
	 * @param r
	 */
	private void executeUntilSucceedsWithShutdown(def entity, Runnable r) {
		try {

			Exception lastException = null;
			for(int attempt = 1; attempt <= 30; attempt++) {
				try {
					r.run();
					return;
				} catch(Exception e) {
					lastException = e
					logger.trace "Attempt ${attempt}: ${e.message}"
					Thread.sleep 1000
				}
			}
			if (lastException != null)
				throw lastException
		} finally {
			entity.shutdown()
		}
	}
}
