package org.overpaas.web.tomcat;

import static org.junit.Assert.*

import java.util.Map;

import groovy.transform.InheritConstructors

import org.junit.Test
import org.overpaas.core.locations.SshMachineLocation
import org.overpaas.core.types.common.AbstractOverpaasApplication

class TomcatNodeTest {

	@InheritConstructors
	class Application extends AbstractOverpaasApplication {
		public Application(Map properties=[:]) {
			super(properties);
		}
	}

	@Test
	public void accepts_location_as_start_parameter() {
		Application app = new Application();
		TomcatNode tc = new TomcatNode(parent: app);
		tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
		tc.shutdown()
	}
	
	@Test
	public void accepts_location_in_entity() {
		Application app = new Application(location: new SshMachineLocation(name:'london', host:'localhost'));
		TomcatNode tc = new TomcatNode(parent: app);
		tc.start()
		tc.shutdown()
	}
	
	@Test
	public void accepts_entity_location_same_as_start_parameter() {
		Application app = new Application();
		TomcatNode tc = new TomcatNode(parent: app, location: new SshMachineLocation(name:'london', host:'localhost'));
		tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
		tc.shutdown()
	}
	
	@Test
	public void reject_if_entity_location_conflicts_with_start_parameter() {
		Application app = new Application()
		boolean caught = false
		TomcatNode tc = new TomcatNode(parent: app, location: new SshMachineLocation(name:'tokyo', host:'localhost'))
		try {
			tc.start([:], null, new SshMachineLocation(name:'london', host:'localhost'))
			tc.shutdown()
		} catch(Exception e) {
			caught = true
		}
		assertEquals(true, caught)
	}
	
	@Test
	public void reject_if_location_not_in_entity_or_in_start_parameter() {
		Application app = new Application();
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
}
