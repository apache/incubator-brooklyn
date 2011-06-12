package org.overpaas.web.tomcat;

import static org.junit.Assert.*;

import groovy.transform.InheritConstructors

import java.util.Map

import org.junit.Test
import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Application;
import org.overpaas.locations.SshMachineLocation

class TomcatNodeTest {
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
}
