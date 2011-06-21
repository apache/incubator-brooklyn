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
    
//    @Test
    public void expectedJBossArtifactInstalled() {
        // MD5 obtained from http://sourceforge.net/projects/jboss/files/JBoss/JBoss-6.0.0.Final/jboss-as-distribution-6.0.0.Final-src.zip.md5/download
        String expectedMD5 = "31840454bb2f28d9bc891e6fa38d835b";
        def jb = new JBossNode()
        def loc = new SshMachineLocation(host:"localhost")
        def setup = new JBoss6SshSetup(jb)

        loc.run(setup.getInstallScript())

        def installedTo = setup.installDir
        // Simple way to get md5 or sha1?
    }

    @Test
    public void canStartupAndShutdown() {
        Application app = new TestApplication();
        JBossNode tc = new JBossNode(parent:app);
        println "---------- Starting"
        tc.start(location: new SshMachineLocation(name:'london', host:'localhost'))
        println "---------- Started, shutting down"
        tc.shutdown()
        println "---------- Done"
    }

}
