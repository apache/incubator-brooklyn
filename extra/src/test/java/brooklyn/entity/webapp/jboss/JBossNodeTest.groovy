package brooklyn.entity.webapp.jboss;

import static org.junit.Assert.*

import java.security.*
import java.util.Map

import org.junit.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.jboss.JBoss6SshSetup
import brooklyn.location.basic.SshMachineLocation

class JBossNodeTest {

    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }
    
    @Test
    public void canStartupAndShutdown() {
        Application app = new TestApplication();
        JBossNode tc = new JBossNode(owner:app);
        println "---------- Starting"
        tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
        println "---------- Started, shutting down"
        tc.shutdown()
        println "---------- Done"
    }

}
