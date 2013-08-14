package brooklyn.test.entity

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.EffectorAndBody
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.SoftwareProcessImpl
import brooklyn.entity.java.VanillaJavaApp
import brooklyn.entity.webapp.WebAppServiceConstants
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

/**
 * Mock web application server entity for testing.
 */
public class TestJavaWebAppEntity extends VanillaJavaApp {
	protected static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntity)
    public static final Effector<Void> START = new EffectorAndBody<Void>(SoftwareProcessImpl.START, new MethodEffector(TestJavaWebAppEntity.class, "customStart").getBody());

    public TestJavaWebAppEntity(Map properties=[:], Entity parent=null) {
        super(properties, parent)
    }
    
    @SetFromFlag int a;
    @SetFromFlag int b;
    @SetFromFlag int c;

	public void waitForHttpPort() { }

    
	public void customStart(Collection<? extends Location> loc) {
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
