package brooklyn.entity.webapp.tomcat;

import static brooklyn.test.TestUtils.isPortInUse;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.ServerSocket;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestApplication2;

import com.google.common.collect.ImmutableList;

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * FIXME this test is largely superseded by WebApp*IntegrationTest which tests inter alia Tomcat
 */
public class TomcatServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerIntegrationTest.class);
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880;

    static boolean httpPortLeftOpen = false;
    private int oldHttpPort = -1;
    
    private TestApplication2 app;
    private TomcatServer tc;
    
    @BeforeMethod(alwaysRun=true)
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail("someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost");
        }
    }
 
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test(groups="Integration")
    public void detectFailureIfTomcatCantBindToPort() throws Exception {
        ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
        try {
            app = (TestApplication2) ApplicationBuilder.builder(TestApplication2.class).manage();
            tc = app.createAndManageChild(BasicEntitySpec.newInstance(TomcatServer.class).configure("httpPort",DEFAULT_HTTP_PORT));
            
            try {
                tc.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
                fail("Should have thrown start-exception");
            } catch (Exception e) {
                // LocalhostMachineProvisioningLocation does NetworkUtils.isPortAvailable, so get -1
                IllegalArgumentException iae = Throwables2.getFirstThrowableOfType(e, IllegalArgumentException.class);
                if (iae == null || iae.getMessage() == null || !iae.getMessage().equals("port for httpPort is null")) throw e;
            } finally {
                tc.stop();
            }
            assertFalse(tc.getAttribute(TomcatServerImpl.SERVICE_UP));
        } finally {
            listener.close();
        }
    }

	//TODO should define a generic mechanism for doing this    
////    @Test(groups = [ "Integration" ])
//    public void createsPropertiesFilesWithEnvironmentVariables() {
//        app = new TestApplication();
//        tc = new TomcatServer(parent:app, httpPort:DEFAULT_HTTP_PORT);
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
//    
//    private String getEnvironmentVariable(TomcatServerImpl tomcat, String var) {
//        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
//        int result = tomcat.driver.machine.run(out:outstream, ["env"], tomcat.driver.runEnvironment);
//        String outstr = new String(outstream.toByteArray());
//        String[] outLines = outstr.split("\n");
//        for (String line : outLines) {
//            String[] envVariable = line.trim().split("=");
//            if (envVariable != null && envVariable[0] == var) return envVariable[1];
//        }
//        throw new IllegalStateException("environment variable '$var' not found in $outstr")
//    }
//    
//    private void assertPropertiesEquals(Properties props, Map expected) {
//        assertEquals(props.stringPropertyNames(), expected.keySet());
//        for (String key : props.stringPropertyNames()) {
//            assertEquals(props.getProperty(key), expected.get(key));
//        }
//    }
}
