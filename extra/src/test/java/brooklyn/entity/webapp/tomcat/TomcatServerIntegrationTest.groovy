package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
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

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * FIXME this test is largely superseded by WebApp*IntegrationTest which tests inter alia Tomcat
 */
public class TomcatServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerIntegrationTest.class)
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880

    static { TimeExtras.init() }

    static boolean httpPortLeftOpen = false;
    private int oldHttpPort = -1;
    
    Application app
    TomcatServer tc
    
    @BeforeMethod(groups = [ "Integration" ])
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;
        Integer shutdownPort = tc?.getAttribute(TomcatServer.TOMCAT_SHUTDOWN_PORT)
        
        if (shutdownPort != null) {
            boolean socketClosed = new Repeater("Checking Tomcat has shut down")
                .repeat {
    		            if (shutdownSocket) shutdownSocket.close();
    		            try { shutdownSocket = new Socket(InetAddress.localHost, shutdownPort); }
    		            catch (SocketException e) { gotException = e; return; }
    		            gotException = null
    		        }
                .every(100 * MILLISECONDS)
                .until { gotException }
                .limitIterationsTo(25)
    	        .run();
    
            if (socketClosed == false) {
                LOG.error "Tomcat did not shut down - this is a failure of the last test run";
                LOG.warn "I'm sending a message to the Tomcat shutdown port";
                OutputStreamWriter writer = new OutputStreamWriter(shutdownSocket.getOutputStream());
                writer.write("SHUTDOWN\r\n");
                writer.flush();
                writer.close();
                shutdownSocket.close();
                throw new Exception("Last test run did not shut down Tomcat")
            }
        } else {
            LOG.info "Cannot shutdown, because shutdown-port not set for $tc";
        }
    }

    @Test(groups = [ "Integration" ])
    public void detectFailureIfTomcatCantBindToPort() {
        ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
        Thread t = new Thread({ try { for(;;) { Socket socket = listener.accept(); socket.close(); } } catch(Exception e) {} })
        t.start()
        try {
            app = new TestApplication()
            tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT)
            Exception caught = null
            try {
                tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
                fail("Should have thrown start-exception")
            } catch (IllegalArgumentException e) {
                // LocalhostMachineProvisioningLocation does NetworkUtils.isPortAvailable, so get -1
                caught = e
                assertEquals e.message, "Invalid port value -1: httpPort (suggested $DEFAULT_HTTP_PORT)"
            } catch (EntityStartException e) {
                caught = e
                assertEquals e.message, "HTTP service on port ${DEFAULT_HTTP_PORT} failed"
            } finally {
                tc.stop()
            }
            assertFalse tc.getAttribute(TomcatServer.SERVICE_UP)
        } finally {
            listener.close();
            t.join();
        }
    }

	//TODO should define a generic mechanism for doing this    
////    @Test(groups = [ "Integration" ])
//    public void createsPropertiesFilesWithEnvironmentVariables() {
//        app = new TestApplication();
//        tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);
//        tc.setConfig(TomcatServer.PROPERTY_FILES.subKey("MYVAR1"),[akey:"aval",bkey:"bval"])
//        tc.setConfig(TomcatServer.PROPERTY_FILES.subKey("MYVAR2"),[ckey:"cval",dkey:"dval"])
//        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
//        
//        try {
//            SshMachineLocation machine = tc.locations.first()
//            String var1file = getEnvironmentVariable(tc, "MYVAR1")
//            String var2file = getEnvironmentVariable(tc, "MYVAR2")
//            File tmpFile1 = File.createTempFile("one", "tmp", new File("/tmp"))
//            File tmpFile2 = File.createTempFile("two", "tmp", new File("/tmp"))
//            tmpFile1.deleteOnExit()
//            tmpFile2.deleteOnExit()
//            machine.copyFrom var1file, tmpFile1.absolutePath
//            machine.copyFrom var2file, tmpFile2.absolutePath
//            
//            Properties var1props = new Properties()
//            var1props.load(new FileInputStream(tmpFile1))
//            
//            Properties var2props = new Properties()
//            var2props.load(new FileInputStream(tmpFile2))
//            
//            assertPropertiesEquals(var1props, [akey:"aval",bkey:"bval"])
//            assertPropertiesEquals(var2props, [ckey:"cval",dkey:"dval"])
//        } finally {
//            tc.stop()
//        }
//    }
    
    private String getEnvironmentVariable(TomcatServer tomcat, String var) {
        ByteArrayOutputStream outstream = new ByteArrayOutputStream()
        int result = tomcat.driver.machine.run(out:outstream, ["env"], tomcat.driver.runEnvironment)
        String outstr = new String(outstream.toByteArray())
        String[] outLines = outstr.split("\n")
        for (String line in outLines) {
            String[] envVariable = line.trim().split("=")
            if (envVariable && envVariable[0] == var) return envVariable[1]
        }
        throw new IllegalStateException("environment variable '$var' not found in $outstr")
    }
    
    private void assertPropertiesEquals(Properties props, Map expected) {
        assertEquals(props.stringPropertyNames(), expected.keySet())
        for (String key in props.stringPropertyNames()) {
            assertEquals(props.getProperty(key), expected.get(key))
        }
    }
}
