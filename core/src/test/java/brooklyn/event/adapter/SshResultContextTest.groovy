package brooklyn.event.adapter;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestEntityImpl

@Deprecated // Class under test is deprecated
public class SshResultContextTest {

    final static LocalhostMachineProvisioningLocation location = [ count:1 ]
    final static SshMachineLocation machine = location.obtain()

	final static SshResultContext SIMPLE_RESPONSE = [ machine, 0, "output", "error" ]
	final static SshResultContext NUMERIC_RESPONSE = [ machine, 0, "31337", "error" ]
	final static SshResultContext ERROR_RESPONSE = [ machine, new IOException("mock") ]

	@Test
	public void testStdout() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ stdout }), "output");
	}

	@Test
	public void testStderr() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ stderr }), "error");
	}

	@Test
	public void testExitStatus() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ exitStatus == 0 }), true);
	}

	@Test
	public void testSensorAndEntityAvailable() {
		BasicAttributeSensor s = [ String, "name", "Description" ];
		Entity e = new TestEntityImpl();
		assertEquals(SIMPLE_RESPONSE.evaluate(entity: e, sensor: s, { sensor==s && entity==e }), true);
		assertEquals(SIMPLE_RESPONSE.evaluate(e, s, { sensor==s && entity==e }), true);
		assertEquals(SIMPLE_RESPONSE.evaluate(e, null, { sensor!=s && entity==e }), true);
	}

	@Test
	public void testSensorEvalErrorThrown() {
		try {
			SIMPLE_RESPONSE.evaluate({ throw new IllegalStateException("mock") });
			fail "should have thrown here!"
		} catch (IllegalStateException e) {
			assertTrue e.toString().contains("mock")
		}
	}

	@Test
	public void testNoError() {
		assertEquals SIMPLE_RESPONSE.evaluate({ error!=null }), false
	}

	@Test
	public void testError() {
		assertEquals ERROR_RESPONSE.evaluate({ error!=null }), true
	}

	@Test
	public void testHttpErrorPreventsSensorEvalError() {
		assertEquals ERROR_RESPONSE.evaluate({ throw new IllegalStateException("mock") }), SshResultContext.UNSET
	}
}
