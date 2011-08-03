package brooklyn.entity.nosql.infinispan

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.Map
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.webapp.tomcat.Tomcat7SshSetup
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.EntityStartException
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.test.TestUtils.BooleanWithMessage
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

class Infinispan5ServerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(Infinispan5ServerIntegrationTest.class)
    
    static String DEFAULT_PROTOCOL = "memcached"
    static int DEFAULT_PORT = 11219

    static boolean portLeftOpen = false;
    
    static { TimeExtras.init() }

    @BeforeMethod(groups = [ "Integration" ])
    public void failIfPortInUse() {
        if (isPortInUse(DEFAULT_PORT, 5000L)) {
            portLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_PORT; tests assume that port $DEFAULT_PORT is free on localhost"
        }
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        boolean socketClosed = new Repeater("Checking Tomcat has shut down")
            .repeat({
                    if (shutdownSocket) shutdownSocket.close();
                    try { shutdownSocket = new Socket(InetAddress.localHost, DEFAULT_PORT); }
                    catch (SocketException e) { gotException = e; return; }
                    gotException = null
                })
            .every(100, TimeUnit.MILLISECONDS)
            .until({ gotException })
            .limitIterationsTo(25)
            .run();

        if (socketClosed == false) {
            logger.error "Infinispan did not shut down";
            throw new Exception("Infinispan did not shut down")
        }
    }

    @Test(groups = [ "Integration" ])
    public void testInfinispanStartsAndStops() {
        final Infinispan5Server infini = new Infinispan5Server(owner:new TestApplication())
        infini.setConfig(Infinispan5Server.PORT.getConfigKey(), DEFAULT_PORT)
        infini.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue infini.getAttribute(Infinispan5Server.SERVICE_UP)
        }, {
            infini.stop()
        })
    }
}
