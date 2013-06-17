package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.webapp.HttpsSslConfig;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * TODO re-write this like WebAppIntegrationTest, rather than being jboss7 specific.
 */
public class Jboss7ServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(Jboss7ServerIntegrationTest.class);
    
    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication app;
    private File keystoreFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    	String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        keystoreFile = createTemporaryKeyStore("myname", "mypass");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
        if (keystoreFile != null) keystoreFile.delete();
    }

    private File createTemporaryKeyStore(String alias, String password) throws Exception {
        FluentKeySigner signer = new FluentKeySigner("brooklyn-test").selfsign();
        
        KeyStore ks = SecureKeys.newKeyStore();
        ks.setKeyEntry(
                alias, 
                signer.getKey().getPrivate(), 
                password.toCharArray(), 
                new Certificate[] { signer.getAuthorityCertificate() });
        
        File file = File.createTempFile("test", "keystore");
        FileOutputStream fos = new FileOutputStream(file);
        try {
            ks.store(fos, "mypass".toCharArray());
            return file;
        } finally {
            Closeables.closeQuietly(fos);
        }
    }
    
    @Test(groups = "Integration")
    public void testHttp() throws Exception {
        final JBoss7Server server = app.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("war", warUrl.toString()));
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";
        
        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpUrl.toLowerCase());
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");
        
        HttpTestUtils.assertUrlUnreachable(httpsUrl);

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertNotNull(server.getAttribute(JBoss7Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.TOTAL_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.MAX_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_RECEIVED));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_SENT));
            }});
    }

    @Test(groups = {"Integration"})
    public void testHttps() throws Exception {
        final JBoss7Server server = app.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("war", warUrl.toString())
                .configure(JBoss7Server.ENABLED_PROTOCOLS, ImmutableList.of("https"))
                .configure(JBoss7Server.HTTPS_SSL_CONFIG, new HttpsSslConfig().keyAlias("myname").keystorePassword("mypass").keystoreUrl(keystoreFile.getAbsolutePath())));
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";
        
        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpsUrl.toLowerCase());
        
        HttpTestUtils.assertUrlUnreachable(httpUrl);
        
        // FIXME HttpTestUtils isn't coping with https, giving
        //     javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
        // Uncomment this as soon as HttpTestUtils is fixed
        // Manual inspection with breakpoint and web-browser confirmed this was working
//        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpsUrl, 200);
//        HttpTestUtils.assertContentContainsText(httpsUrl, "Hello");
        
        // FIXME querying for http://localhost:9990/management/subsystem/web/connector/http/read-resource?include-runtime=true
        // gives 500 when http is disabled, but if miss out "?include-runtime=true" then it works fine.
        // So not getting these metrics!
//        TestUtils.executeUntilSucceeds(new Runnable() {
//            public void run() {
//                assertNotNull(server.getAttribute(JBoss7Server.REQUEST_COUNT));
//                assertNotNull(server.getAttribute(JBoss7Server.ERROR_COUNT));
//                assertNotNull(server.getAttribute(JBoss7Server.TOTAL_PROCESSING_TIME));
//                assertNotNull(server.getAttribute(JBoss7Server.MAX_PROCESSING_TIME));
//                assertNotNull(server.getAttribute(JBoss7Server.BYTES_RECEIVED));
//                assertNotNull(server.getAttribute(JBoss7Server.BYTES_SENT));
//            }});
    }
    
    @Test(groups = {"Integration"})
    public void testHttpAndHttps() throws Exception {
        final JBoss7Server server = app.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("war", warUrl.toString())
                .configure(JBoss7Server.ENABLED_PROTOCOLS, ImmutableList.of("http", "https"))
                .configure(JBoss7Server.HTTPS_SSL_CONFIG, new HttpsSslConfig().keyAlias("myname").keystorePassword("mypass").keystoreUrl(keystoreFile.getAbsolutePath())));
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";

        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpsUrl.toLowerCase());

        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");
        
        // FIXME HttpTestUtils isn't coping with https, giving
        //     javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
        // Uncomment this as soon as HttpTestUtils is fixed
        // Manual inspection with breakpoint and web-browser confirmed this was working
        //HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpsUrl, 200);
        //HttpTestUtils.assertContentContainsText(httpsUrl, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertNotNull(server.getAttribute(JBoss7Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.TOTAL_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.MAX_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_RECEIVED));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_SENT));
            }});
    }

    @Test(groups = {"Integration"})
    public void testUsingPortOffsets() throws Exception {
        final JBoss7Server serverA = app.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("portIncrement", 100));
        final JBoss7Server serverB = app.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("portIncrement", 200));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertNotNull(serverA.getAttribute(JBoss7Server.BYTES_SENT));
                assertNotNull(serverB.getAttribute(JBoss7Server.BYTES_SENT));
            }});
    }

}
