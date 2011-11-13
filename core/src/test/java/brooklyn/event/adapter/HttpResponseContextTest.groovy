package brooklyn.event.adapter;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestEntity

public class HttpResponseContextTest {

	final static HttpResponseContext SIMPLE_RESPONSE = [ null, 400, [foo:["1"], bar:["2"]], "A TEST", null ]
	final static HttpResponseContext ERROR_RESPONSE = [ null, new IOException("mock") ]
	final static HttpResponseContext JSON_RESPONSE = [ null, 400, [:], '{"aStr":"9", "bStr":"8", "anInt":10, "aMap":{"x":"a"}}', null ]

	@Test
	public void testContent() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ content }), "A TEST");
	}
	@Test
	public void testResult() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ responseCode == 400 }), true);
	}
	@Test
	public void testHeaderString() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ headers.foo }), "1");
	}
	@Test
	public void testHeaderNoneSuch() {
		assertEquals(SIMPLE_RESPONSE.evaluate({ headers.baz }), null);
	}
	@Test
	public void testSensorAndEntityAvailable() {
		BasicAttributeSensor s = [String.class, "aString", ""];
		Entity e = new TestEntity();
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
			assertTrue e.toString().indexOf("mock")>=0
		}
	}

	@Test
	public void testNoHttpError() {
		assertEquals SIMPLE_RESPONSE.evaluate({ error!=null }), false
	}

	@Test
	public void testHttpError() {
		assertEquals ERROR_RESPONSE.evaluate({ error!=null }), true
	}

	@Test
	public void testHttpErrorPreventsSensorEvalError() {
		assertEquals ERROR_RESPONSE.evaluate({ throw new IllegalStateException("mock") }), HttpResponseContext.UNSET
	}

	@Test
	public void testJsonField() {
		assertEquals(JSON_RESPONSE.evaluate({ json.aStr }), "9");
		assertEquals(JSON_RESPONSE.evaluate({ json.anInt }), 10);
	}
	@Test
	public void testJsonSubfield() {
		assertEquals(JSON_RESPONSE.evaluate({ json.aMap.x }), "a");
	}

}
