package brooklyn.launcher;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BrooklynWebServerTest {

    public static final Logger log = LoggerFactory.getLogger(BrooklynWebServer.class);

    private BrooklynProperties brooklynProperties;
    private BrooklynWebServer webServer;
    private List<LocalManagementContext> managementContexts = Lists.newCopyOnWriteArrayList();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp(){
        brooklynProperties = BrooklynProperties.Factory.newDefault();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (LocalManagementContext managementContext : managementContexts) {
            Entities.destroyAll(managementContext);
        }
        managementContexts.clear();
        if (webServer != null) webServer.stop();
    }
    
    private LocalManagementContext newManagementContext(BrooklynProperties brooklynProperties) {
        LocalManagementContext result = new LocalManagementContext(brooklynProperties);
        managementContexts.add(result);
        return result;
    }
    
    @Test
    public void verifyHttp() throws Exception {
        webServer = new BrooklynWebServer(newManagementContext(brooklynProperties));
        try {
            webServer.start();
    
            HttpToolResponse response = HttpTool.execAndConsume(new DefaultHttpClient(), new HttpGet(webServer.getRootUrl()));
            assertEquals(response.getResponseCode(), 200);
        } finally {
            webServer.stop();
        }
    }

    @Test
    public void verifyHttps() throws Exception {
        Map<String,?> flags = ImmutableMap.<String,Object>builder()
                .put("httpsEnabled", true)
                .put("keystorePath", getFile("server.ks"))
                .put("keystorePassword", "password")
                .put("truststorePath", getFile("server.ts"))
                .put("trustStorePassword", "password")
                .build();
        webServer = new BrooklynWebServer(flags, newManagementContext(brooklynProperties));
        webServer.start();
        
        try {
            KeyStore keyStore = load("client.ks", "password");
            KeyStore trustStore = load("client.ts", "password");
            SSLSocketFactory socketFactory = new SSLSocketFactory(SSLSocketFactory.TLS, keyStore, "password", trustStore, (SecureRandom)null, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpToolResponse response = HttpTool.execAndConsume(
                    HttpTool.httpClientBuilder()
                            .port(webServer.getActualPort())
                            .https(true)
                            .socketFactory(socketFactory)
                            .build(),
                    new HttpGet(webServer.getRootUrl()));
            assertEquals(response.getResponseCode(), 200);
        } finally {
            webServer.stop();
        }
    }

    private KeyStore load(String name, String password) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream instream = new FileInputStream(new File(getFile(name)));
        keystore.load(instream, password.toCharArray());
        return keystore;
    }

    private String getFile(String file) {
        return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
    }
}
