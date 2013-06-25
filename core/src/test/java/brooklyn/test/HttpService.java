package brooklyn.test;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;

import com.google.common.base.Optional;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.crypto.SecureKeys;

/**
 * Starts an in-memory web-server, which for example can be used for testing HttpFeed.
 * 
 * In particular, this is useful for testing https. For normal http, the com.google.mockwebserver.MockWebServer 
 * is a lighter weight and more easily controlled alternative. However, I (Aled) couldn't get 
 * mockwebserver.useHttps(...) to work so resorted to this class for testing. 
 * 
 * @author aled
 */
public class HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    public static final String ROOT_WAR_URL = "classpath://hello-world.war";
    public static final String SERVER_KEYSTORE = "classpath://server.ks";
    
    private final boolean httpsEnabled;
    private final InetAddress addr;
    private final int actualPort;
    private final Server server;
    private volatile Thread shutdownHook;

    private Optional<? extends SecurityHandler> securityHandler = Optional.absent();

    public HttpService(PortRange portRange) {
        this(portRange, false);
    }

    public HttpService(PortRange portRange, boolean httpsEnabled) {
        this.httpsEnabled = httpsEnabled;
        addr = LocalhostMachineProvisioningLocation.getLocalhostInetAddress();
        actualPort = LocalhostMachineProvisioningLocation.obtainPort(addr, portRange);
        server = new Server(actualPort);
    }

    /**
     * Enables basic HTTP authentication on the server.
     */
    public HttpService basicAuthentication(String username, String password) {
        HashLoginService l = new HashLoginService();
        l.putUser(username, Credential.getCredential(password), new String[]{"user"});
        l.setName("test-realm");

        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "user");
        constraint.setAuthenticate(true);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("test-realm");
        csh.addConstraintMapping(constraintMapping);
        csh.setLoginService(l);

        this.securityHandler = Optional.of(csh);

        return this;
    }

    public HttpService start() throws Exception {
        try {
            if (httpsEnabled) {
                //by default the server is configured with a http connector, this needs to be removed since we are going
                //to provide https
                for (Connector c: server.getConnectors()) {
                    server.removeConnector(c);
                }
    
                InputStream keyStoreStream = new ResourceUtils(this).getResourceFromUrl(SERVER_KEYSTORE);
                KeyStore keyStore;
                try {
                    keyStore = SecureKeys.newKeyStore(keyStoreStream, "password");
                } finally {
                    keyStoreStream.close();
                }
                
                SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setTrustAll(true);
                sslContextFactory.setKeyStorePassword("password");

                SslSocketConnector sslSocketConnector = new SslSocketConnector(sslContextFactory);
                sslSocketConnector.setPort(actualPort);
                server.addConnector(sslSocketConnector);
            }
    
            addShutdownHook();
    
            File tmpWarFile = ResourceUtils.writeToTempFile(
                    new ResourceUtils(this).getResourceFromUrl(ROOT_WAR_URL), 
                    "TestHttpService", 
                    ".war");
            
            WebAppContext context = new WebAppContext();
            context.setWar(tmpWarFile.getAbsolutePath());
            context.setContextPath("/");
            context.setParentLoaderPriority(true);

            if (securityHandler.isPresent()) {
                context.setSecurityHandler(securityHandler.get());
            }

            server.setHandler(context);
            server.start();
    
            log.info("Started test HttpService at "+getUrl());
            
        } catch (Exception e) {
            try {
                shutdown();
            } catch (Exception e2) {
                log.warn("Error shutting down HttpService while recovering from earlier error (re-throwing earlier error)", e2);
                throw e;
            }
        }

        return this;
    }
    
    public void shutdown() throws Exception {
        if (server==null) return;
        if (shutdownHook != null) ResourceUtils.removeShutdownHook(shutdownHook);
        String url = getUrl();
        if (log.isDebugEnabled())
            log.debug("Stopping Test HttpService at {}", url);

        server.stop();
        try {
            server.join();
        } catch (Exception e) {
            /* NPE may be thrown e.g. if threadpool not started */
        }
        LocalhostMachineProvisioningLocation.releasePort(addr, actualPort);
        log.info("Stopped test HttpService at {}", url);
    }

    public String getUrl() {
        if (actualPort > 0) {
            String protocol = httpsEnabled ? "https" : "http";
            return protocol+"://"+addr.getHostName()+":"+actualPort+"/";
        } else {
            return null;
        }
    }

    private void addShutdownHook() {
        // some webapps can generate a lot of output if we don't shut down the browser first
        shutdownHook = ResourceUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                log.info("Test HttpService detected shut-down: stopping");
                try {
                    shutdown();
                } catch (Exception e) {
                    log.error("Failure shutting down web-console: "+e, e);
                }
            }
        });
    }

}
