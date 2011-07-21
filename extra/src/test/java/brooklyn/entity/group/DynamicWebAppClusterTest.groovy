package brooklyn.entity.group;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*
import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.internal.TimeExtras

public class DynamicWebAppClusterTest {

    static { TimeExtras.init() }
    
    @Test
    public void testRequestCountAggregation() {
        Application app = new TestApplication()
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            initialSize: 2,
            newEntity: { properties -> new TestEntity(properties) },
            owner:app)
        cluster.start([new GeneralPurposeLocation()])
        
        cluster.members.each { it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS, {
            assertEquals 2, cluster.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)
        })
        
        cluster.members.each { it.spoofRequest(); it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS, {
            assertEquals 3d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        })
    }
    
    @InheritConstructors
    private static class TestApplication extends AbstractApplication {
        @Override String toString() { return "Application["+id[-8..-1]+"]" }
    }
 
    @InheritConstructors
    private static class TestEntity extends JavaWebApp {
        private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)
        
        void restart() {}
        void waitForHttpPort() {}
        SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) { null }
        void initJmxSensors() {}
        
        void start(Collection<? extends Location> loc) { logger.trace "Start"; }
        void stop() { logger.trace "Stop $this"; }
        @Override String toString() { return "Entity["+id[-8..-1]+"]" }
        
        public synchronized void spoofRequest() { 
            def rc = getAttribute(JavaWebApp.REQUEST_COUNT) ?: 0
            setAttribute(JavaWebApp.REQUEST_COUNT, rc+1)
        }
        
    }
    
}
