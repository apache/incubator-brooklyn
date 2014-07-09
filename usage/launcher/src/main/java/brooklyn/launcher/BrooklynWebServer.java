/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.launcher;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.ConfigKey;
import brooklyn.launcher.config.CustomResourceLocator;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.BrooklynRestApi;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
import brooklyn.rest.util.HaMasterCheckFilter;
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.BrooklynNetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.javalang.Threads;
import brooklyn.util.logging.LoggingSetup;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.web.ContextHandlerCollectionHotSwappable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.container.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * Starts the web-app running, connected to the given management context
 */
public class BrooklynWebServer {
    private static final Logger log = LoggerFactory.getLogger(BrooklynWebServer.class);

    public static final String BROOKLYN_WAR_URL = "classpath://brooklyn.war";
    static {
        // support loading the WAR in dev mode from an alternate location 
        CustomResourceLocator.registerAlternateLocator(new CustomResourceLocator.SearchingClassPathInDevMode(
                BROOKLYN_WAR_URL, "/usage/launcher/target", 
                "/usage/jsgui/target/brooklyn-jsgui-"+BrooklynVersion.get()+".war"));
    }
    
    static {
        LoggingSetup.installJavaUtilLoggingBridge();
    }
    
    protected Server server;

    private WebAppContext rootContext;
    
    @SetFromFlag
    protected PortRange port = PortRanges.fromString("8081+");
    @SetFromFlag
    protected PortRange httpsPort = PortRanges.fromString("8443+");
    
    /** actual port where this gets bound; will be consistent with the "port" passed in
     * but that might be a range and here it is a single port, or -1 if not yet set */
    protected volatile int actualPort = -1;
    /** actual NIC where this is listening; in the case of 0.0.0.0 being passed in as bindAddress,
     * this will revert to one address (such as localhost) */
    protected InetAddress actualAddress = null;

    @SetFromFlag
    protected String war = BROOKLYN_WAR_URL;

    /** IP of NIC where this server should bind, or null to autodetect 
     * (e.g. 0.0.0.0 if security is configured, or loopback if no security) */
    @SetFromFlag
    protected InetAddress bindAddress = null;

    /** The URI that this server's management context will be publically available on. */
    @SetFromFlag
    protected URI publicAddress = null;

    /**
     * map of context-prefix to file
     */
    @SetFromFlag
    private Map<String, String> wars = new LinkedHashMap<String, String>();

    @SetFromFlag
    protected boolean ignoreWebappDeploymentFailures = false;

    @SetFromFlag
    private Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    private ManagementContext managementContext;

    @SetFromFlag
    private Boolean httpsEnabled;

    @SetFromFlag
    private String sslCertificate;

    @SetFromFlag
    private String keystorePath;

    @SetFromFlag
    private String keystorePassword;

    @SetFromFlag
    private String keystoreCertAlias;

    @SetFromFlag
    private String truststorePath;

    @SetFromFlag
    private String trustStorePassword;

    private File webappTempDir;
    
    private Class<BrooklynPropertiesSecurityFilter> securityFilterClazz;

    public BrooklynWebServer(ManagementContext managementContext) {
        this(Maps.newLinkedHashMap(), managementContext);
    }

    /**
     * accepts flags:  port,
     * war (url of war file which is the root),
     * wars (map of context-prefix to url),
     * attrs (map of attribute-name : object pairs passed to the servlet)
     */
    public BrooklynWebServer(Map<?,?> flags, ManagementContext managementContext) {
        this.managementContext = managementContext;
        Map<?,?> leftovers = FlagUtils.setFieldsFromFlags(flags, this);
        if (!leftovers.isEmpty())
            log.warn("Ignoring unknown flags " + leftovers);
        
        webappTempDir = BrooklynServerConfig.getBrooklynWebTmpDir(managementContext);
    }

    public BrooklynWebServer(ManagementContext managementContext, int port) {
        this(managementContext, port, "brooklyn.war");
    }

    public BrooklynWebServer(ManagementContext managementContext, int port, String warUrl) {
        this(MutableMap.of("port", port, "war", warUrl), managementContext);
    }

    public void setSecurityFilter(Class<BrooklynPropertiesSecurityFilter> filterClazz) {
        this.securityFilterClazz = filterClazz;
    }

    public BrooklynWebServer setPort(Object port) {
        if (getActualPort()>0)
            throw new IllegalStateException("Can't set port after port has been assigned to server (using "+getActualPort()+")");
        this.port = TypeCoercions.coerce(port, PortRange.class);
        return this;
    }

    @VisibleForTesting
    File getWebappTempDir() {
        return webappTempDir;
    }
    
    public boolean getHttpsEnabled() {
        if (httpsEnabled!=null) return httpsEnabled;
        httpsEnabled = managementContext.getConfig().getConfig(BrooklynWebConfig.HTTPS_REQUIRED);
        return httpsEnabled;
    }
    
    public PortRange getRequestedPort() {
        return port;
    }
    
    /** returns port where this is running, or -1 if not yet known */
    public int getActualPort() {
        return actualPort;
    }

    /** interface/address where this server is listening;
     * if bound to 0.0.0.0 (all NICs, e.g. because security is set) this will return one NIC where this is bound */
    public InetAddress getAddress() {
        return actualAddress;
    }
    
    /** URL for accessing this web server (root context) */
    public String getRootUrl() {
        String address = (publicAddress != null) ? publicAddress.toString() : getAddress().getHostName();
        if (getActualPort()>0){
            String protocol = getHttpsEnabled()?"https":"http";
            return protocol+"://"+address+":"+getActualPort()+"/";
        } else {
            return null;
        }
    }

      /** sets the WAR to use as the root context (only if server not yet started);
     * cf deploy("/", url) */
    public BrooklynWebServer setWar(String url) {
        this.war = url;
        return this;
    }

    /** specifies a WAR to use at a given context path (only if server not yet started);
     * cf deploy(path, url) */
    public BrooklynWebServer addWar(String path, String warUrl) {
        wars.put(path, warUrl);
        return this;
    }

    /** InetAddress to which server should bind;
     * defaults to 0.0.0.0 (although common call path is to set to 127.0.0.1 when security is not set) */
    public BrooklynWebServer setBindAddress(InetAddress address) {
        bindAddress = address;
        return this;
    }

    /**
     * Sets the public address that the server's management context's REST API will be available on
     */
    public BrooklynWebServer setPublicAddress(URI address) {
        publicAddress = address;
        return this;
    }

    /** @deprecated use setAttribute */
    public BrooklynWebServer addAttribute(String field, Object value) {
        return setAttribute(field, value);
    }
    /** Specifies an attribute passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT} */
    public BrooklynWebServer setAttribute(String field, Object value) {
        attributes.put(field, value);
        return this;
    }
    
    public <T> BrooklynWebServer configure(ConfigKey<T> key, T value) {
        return setAttribute(key.getName(), value);
    }

    /** Specifies attributes passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public BrooklynWebServer putAttributes(Map newAttrs) {
        if (newAttrs!=null) attributes.putAll(newAttrs);
        return this;
    }

    public static void installAsServletFilter(ServletContextHandler context) {
        ResourceConfig config = new DefaultResourceConfig();
        // load all our REST API modules, JSON, and Swagger
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);

        // Accept gzipped requests and responses
        config.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS, GZIPContentEncodingFilter.class.getName());
        config.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS, GZIPContentEncodingFilter.class.getName());
        // configure to match empty path, or any thing which looks like a file path with /assets/ and extension html, css, js, or png
        // and treat that as static content
        config.getProperties().put(ServletContainer.PROPERTY_WEB_PAGE_CONTENT_REGEX, "(/?|[^?]*/assets/[^?]+\\.[A-Za-z0-9_]+)");
        // and anything which is not matched as a servlet also falls through (but more expensive than a regex check?)
        config.getFeatures().put(ServletContainer.FEATURE_FILTER_FORWARD_ON_404, true);
        // finally create this as a _filter_ which falls through to a web app or something (optionally)
        FilterHolder filterHolder = new FilterHolder(new ServletContainer(config));

        context.addFilter(filterHolder, "/*", EnumSet.allOf(DispatcherType.class));
    }

    ContextHandlerCollectionHotSwappable handlers = new ContextHandlerCollectionHotSwappable();
    
    /**
     * Starts the embedded web application server.
     */
    public synchronized void start() throws Exception {
        if (server!=null) throw new IllegalStateException(""+this+" already running");

        if (actualPort==-1){
            actualPort = LocalhostMachineProvisioningLocation.obtainPort(getAddress(), getHttpsEnabled()?httpsPort:port);
            if (actualPort == -1) 
                throw new IllegalStateException("Unable to provision port for web console (wanted "+(getHttpsEnabled()?httpsPort:port)+")");
        }

        if (bindAddress!=null) {
            actualAddress = bindAddress;
            server = new Server(new InetSocketAddress(bindAddress, actualPort));
        } else {
            actualAddress = BrooklynNetworkUtils.getLocalhostInetAddress();
            server = new Server(actualPort);
        }

        // use a nice name in the thread pool (otherwise this is exactly the same as Server defaults)
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("brooklyn-jetty-server-"+actualPort+"-"+threadPool.getName());
        server.setThreadPool(threadPool);

        if (log.isDebugEnabled())
            log.debug("Starting Brooklyn console at "+getRootUrl()+", running " + war + (wars != null ? " and " + wars.values() : ""));
        
        if (getHttpsEnabled()) {
            //by default the server is configured with a http connector, this needs to be removed since we are going
            //to provide https
            for (Connector c: server.getConnectors()) {
                server.removeConnector(c);
            }

            SslContextFactory sslContextFactory = new SslContextFactory();

            if (keystorePath==null) keystorePath = managementContext.getConfig().getConfig(BrooklynWebConfig.KEYSTORE_URL);
            if (keystorePassword==null) keystorePassword = managementContext.getConfig().getConfig(BrooklynWebConfig.KEYSTORE_PASSWORD);
            if (keystoreCertAlias==null) keystoreCertAlias = managementContext.getConfig().getConfig(BrooklynWebConfig.KEYSTORE_CERTIFICATE_ALIAS);
            
            if (keystorePath!=null) {
                sslContextFactory.setKeyStorePath(checkFileExists(keystorePath, "keystore"));
                if (Strings.isEmpty(keystorePassword))
                    throw new IllegalArgumentException("Keystore password is required and non-empty if keystore is specified.");
                sslContextFactory.setKeyStorePassword(keystorePassword);
                if (Strings.isNonEmpty(keystoreCertAlias))
                    sslContextFactory.setCertAlias(keystoreCertAlias);
            } else {
                // TODO allow webconsole keystore & related properties to be set in brooklyn.properties 
                log.info("No keystore specified but https enabled; creating a default keystore");
                
                if (Strings.isEmpty(keystoreCertAlias))
                    keystoreCertAlias = "web-console";
                
                // if password is blank the process will block and read from stdin !
                if (Strings.isEmpty(keystorePassword)) {
                    keystorePassword = Identifiers.makeRandomId(8);
                    log.debug("created random password "+keystorePassword+" for ad hoc internal keystore");
                }
                
                KeyStore ks = SecureKeys.newKeyStore();
                KeyPair key = SecureKeys.newKeyPair();
                X509Certificate cert = new FluentKeySigner("brooklyn").newCertificateFor("web-console", key);
                ks.setKeyEntry(keystoreCertAlias, key.getPrivate(), keystorePassword.toCharArray(),
                    new Certificate[] { cert });
                
                sslContextFactory.setKeyStore(ks);
                sslContextFactory.setKeyStorePassword(keystorePassword);
                sslContextFactory.setCertAlias(keystoreCertAlias);
            }
            if (!Strings.isEmpty(truststorePath)) {
                sslContextFactory.setTrustStore(checkFileExists(truststorePath, "truststore"));
                sslContextFactory.setTrustStorePassword(trustStorePassword);
            }

            SslSocketConnector sslSocketConnector = new SslSocketConnector(sslContextFactory);
            sslSocketConnector.setPort(actualPort);
            server.addConnector(sslSocketConnector);
        }

        addShutdownHook();

        MutableMap<String, String> allWars = MutableMap.copyOf(wars);
        String rootWar = allWars.remove("/");
        if (rootWar==null) rootWar = war;
        
        for (Map.Entry<String, String> entry : allWars.entrySet()) {
            String pathSpec = entry.getKey();
            String warUrl = entry.getValue();
            WebAppContext webapp = deploy(pathSpec, warUrl);
            webapp.setTempDirectory(Os.mkdirs(new File(webappTempDir, newTimestampedDirName("war", 8))));
        }
        rootContext = deploy("/", rootWar);
        rootContext.setTempDirectory(Os.mkdirs(new File(webappTempDir, "war-root")));

        if (securityFilterClazz != null) {
            rootContext.addFilter(securityFilterClazz, "/*", EnumSet.allOf(DispatcherType.class));
        }
        rootContext.addFilter(HaMasterCheckFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        installAsServletFilter(rootContext);

        server.setHandler(handlers);
        server.start();
        //reinit required because grails wipes our language extension bindings
        BrooklynLanguageExtensions.reinit();

        if (managementContext instanceof ManagementContextInternal) {
            ((ManagementContextInternal) managementContext).setManagementNodeUri(new URI(getRootUrl()));
        }

        log.info("Started Brooklyn console at "+getRootUrl()+", running " + rootWar + (allWars!=null && !allWars.isEmpty() ? " and " + wars.values() : ""));
    }

    private String newTimestampedDirName(String prefix, int randomSuffixLength) {
        return prefix + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + Identifiers.makeRandomId(randomSuffixLength);
    }
    
    private String checkFileExists(String path, String name) {
        if(!new File(path).exists()){
            throw new IllegalArgumentException("Could not find "+name+": "+path);
        }
        return path;
    }

    /**
     * Asks the app server to stop and waits for it to finish up.
     */
    public synchronized void stop() throws Exception {
        if (server==null) return;
        String root = getRootUrl();
        Threads.removeShutdownHook(shutdownHook);
        if (log.isDebugEnabled())
            log.debug("Stopping Brooklyn web console at "+root+ " (" + war + (wars != null ? " and " + wars.values() : "") + ")");

        server.stop();
        try {
            server.join();
        } catch (Exception e) {
            /* NPE may be thrown e.g. if threadpool not started */
        }
        server = null;
        LocalhostMachineProvisioningLocation.releasePort(getAddress(), actualPort);
        actualPort = -1;
        if (log.isDebugEnabled())
            log.debug("Stopped Brooklyn web console at "+root);
    }

    /** serve given WAR at the given pathSpec; if not yet started, it is simply remembered until start;
     * if server already running, the context for this WAR is started.
     * @return the context created and added as a handler 
     * (and possibly already started if server is started,
     * so be careful with any changes you make to it!)  */
    public WebAppContext deploy(final String pathSpec, final String warUrl) {
        String cleanPathSpec = pathSpec;
        while (cleanPathSpec.startsWith("/"))
            cleanPathSpec = cleanPathSpec.substring(1);
        boolean isRoot = cleanPathSpec.isEmpty();

        WebAppContext context = new WebAppContext();
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
            context.setAttribute(attributeEntry.getKey(), attributeEntry.getValue());
        }

        try {
            File tmpWarFile = Os.writeToTempFile(new CustomResourceLocator(managementContext.getConfig(), ResourceUtils.create(this)).getResourceFromUrl(warUrl), 
                    isRoot ? "ROOT" : ("embedded-" + cleanPathSpec), ".war");
            context.setWar(tmpWarFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to deploy webapp "+pathSpec+" from "+warUrl
                + (ignoreWebappDeploymentFailures ? "; launching run without WAR" : " (rethrowing)")
                + ": "+Exceptions.collapseText(e));
            if (!ignoreWebappDeploymentFailures) {
                throw new IllegalStateException("Failed to deploy webapp "+pathSpec+" from "+warUrl+": "+Exceptions.collapseText(e), e);
            }
            log.debug("Detail on failure to deploy webapp: "+e, e);
            context.setWar("/dev/null");
        }

        context.setContextPath("/" + cleanPathSpec);
        context.setParentLoaderPriority(true);

        deploy(context);
        return context;
    }

    private Thread shutdownHook = null;

    protected synchronized void addShutdownHook() {
        if (shutdownHook!=null) return;
        // some webapps can generate a lot of output if we don't shut down the browser first
        shutdownHook = Threads.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                log.debug("BrooklynWebServer detected shutdown: stopping web-console");
                try {
                    stop();
                } catch (Exception e) {
                    log.error("Failure shutting down web-console: "+e, e);
                }
            }
        });
    }

    public void deploy(WebAppContext context) {
        try {
            handlers.updateHandler(context);
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
    
    public Server getServer() {
        return server;
    }
    
    public WebAppContext getRootContext() {
        return rootContext;
    }

}
