package brooklyn.test.entity

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag
import brooklyn.entity.java.VanillaJavaApp
import brooklyn.entity.java.VanillaJavaAppSshDriver
import brooklyn.entity.webapp.WebAppServiceConstants
import brooklyn.entity.basic.lifecycle.StartStopDriver

/**
 * Mock web application server entity for testing.
 */
public class TestJavaWebAppEntity extends VanillaJavaApp {
	protected static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntity)

    public TestJavaWebAppEntity(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
    
    @SetFromFlag int a;
    @SetFromFlag int b;
    @SetFromFlag int c;

	public void waitForHttpPort() { }

	//public VanillaJavaAppSshDriver newDriver(SshMachineLocation loc) { null }


    @Override
	public void start(Collection<? extends Location> loc) {
        LOG.trace "Starting {}", this
    }

    @Override
	void stop() {
        LOG.trace "Stopping {}", this
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException()
    }

	@Override
    String toString() {
        return "Entity["+id[-8..-1]+"]"
    }

	public synchronized void spoofRequest() {
		def rc = getAttribute(WebAppServiceConstants.REQUEST_COUNT) ?: 0
		setAttribute(WebAppServiceConstants.REQUEST_COUNT, rc+1)
	}
}
