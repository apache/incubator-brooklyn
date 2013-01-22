package brooklyn.entity.webapp.jboss;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.webapp.HttpsSslConfig;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.internal.TimeExtras;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;

/**
 * TODO re-write this like WebAppIntegrationTest, rather than being jboss7 specific.
 */
public class Jboss7ServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(Jboss7ServerIntegrationTest.class);
    
    static { TimeExtras.init(); }

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication app;
    private File keystoreFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    	String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = new TestApplication();
        keystoreFile = createTemporaryKeyStore("myname", "mypass");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
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
        JBoss7Server server = new JBoss7Server(
            MutableMap.builder()
                .put("war", warUrl.toString())
                .build(),
            app);
        Entities.startManagement(app);
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";
        
        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpUrl.toLowerCase());
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");
        
        HttpTestUtils.assertUrlUnreachable(httpsUrl);
    }

    // FIXME HttpTestUtils isn't coping with https, giving
    //     javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
    @Test(groups = {"Integration", "WIP"})
    public void testHttps() throws Exception {
        JBoss7Server server = new JBoss7Server(
            MutableMap.builder()
                .put("war", warUrl.toString())
                .put(JBoss7Server.ENABLED_PROTOCOLS, ImmutableList.of("https"))
                .put(JBoss7Server.HTTPS_SSL_CONFIG, new HttpsSslConfig().keyAlias("myname").keystorePassword("mypass").keystoreUrl(keystoreFile.getAbsolutePath()))
                .build(),
            app);
        Entities.startManagement(app);
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";
        
        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpsUrl.toLowerCase());
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpsUrl, 200);
        HttpTestUtils.assertContentContainsText(httpsUrl, "Hello");
        
        HttpTestUtils.assertUrlUnreachable(httpUrl);
    }
    
    // FIXME HttpTestUtils isn't coping with https, giving
    //     javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
    @Test(groups = {"Integration", "WIP"})
    public void testHttpAndHttps() throws Exception {
        JBoss7Server server = new JBoss7Server(
            MutableMap.builder()
                .put("war", warUrl.toString())
                .put(JBoss7Server.ENABLED_PROTOCOLS, ImmutableList.of("http", "https"))
                .put(JBoss7Server.HTTPS_SSL_CONFIG, new HttpsSslConfig().keyAlias("myname").keystorePassword("mypass").keystoreUrl(keystoreFile.getAbsolutePath()))
                .build(),
            app);
        Entities.startManagement(app);
        
        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTP_PORT)+"/";
        String httpsUrl = "https://"+server.getAttribute(JBoss7Server.HOSTNAME)+":"+server.getAttribute(JBoss7Server.HTTPS_PORT)+"/";

        assertEquals(server.getAttribute(JBoss7Server.ROOT_URL).toLowerCase(), httpsUrl.toLowerCase());

        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpsUrl, 200);
        HttpTestUtils.assertContentContainsText(httpsUrl, "Hello");
    }
}
