package brooklyn.entity.nosql.infinispan

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplicationImpl
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

        boolean socketClosed = new Repeater("Checking Infinispan has shut down")
            .repeat {
                    if (shutdownSocket) shutdownSocket.close();
                    try { shutdownSocket = new Socket(InetAddress.localHost, DEFAULT_PORT); }
                    catch (SocketException e) { gotException = e; return; }
                    gotException = null
                }
            .every(100 * MILLISECONDS)
            .until { gotException }
            .limitIterationsTo(25)
            .run();

        if (socketClosed == false) {
            logger.error "Infinispan did not shut down";
            throw new Exception("Infinispan did not shut down")
        }
    }

    public void ensureIsUp() {
        Socket socket = new Socket(InetAddress.localHost, DEFAULT_PORT);
        socket.close();
    }

    @Test(groups = [ "Integration", "WIP" ])
    public void testInfinispanStartsAndStops() {
        Application app = new TestApplicationImpl();
        try {
            final Infinispan5Server infini = new Infinispan5Server(parent:app)
            infini.setConfig(Infinispan5Server.PORT.getConfigKey(), DEFAULT_PORT)
            infini.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
            
            executeUntilSucceeds {
                assertTrue infini.getAttribute(Infinispan5Server.SERVICE_UP)
            }
        } finally {
            Entities.destroy(app);
        }
    }
}
