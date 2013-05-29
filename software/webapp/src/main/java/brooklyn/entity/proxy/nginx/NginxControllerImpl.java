package brooklyn.entity.proxy.nginx;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.proxy.AbstractControllerImpl;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.TimeExtras;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * An entity that represents an Nginx proxy (e.g. for routing requests to servers in a cluster).
 * <p>
 * The default driver *builds* nginx from source (because binaries are not reliably available, esp not with sticky sessions).
 * This requires gcc and other build tools installed. The code attempts to install them but inevitably 
 * this entity may be more finicky about the OS/image where it runs than others.
 * <p>
 * Paritcularly on OS X we require Xcode and command-line gcc installed and on the path.
 * <p>
 * See {@link http://library.linode.com/web-servers/nginx/configuration/basic} for useful info/examples
 * of configuring nginx.
 * <p>
 * https configuration is supported, with the certificates providable on a per-UrlMapping basis or a global basis.
 * (not supported to define in both places.) 
 * per-Url is useful if different certificates are used for different server names,
 * or different ports if that is supported.
 * see more info on Ssl in {@link ProxySslConfig}.
 */
public class NginxControllerImpl extends AbstractControllerImpl implements NginxController {

    private static final Logger LOG = LoggerFactory.getLogger(NginxControllerImpl.class);
    static { TimeExtras.init(); }
    
    private volatile HttpFeed httpFeed;
    
    public NginxControllerImpl() {
        super();
    }

    public NginxControllerImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public NginxControllerImpl(Map properties){
        this(properties, null);
    }

    public NginxControllerImpl(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void reload() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) {
            Lifecycle state = getAttribute(NginxController.SERVICE_STATE);
            throw new IllegalStateException("Cannot reload (no driver instance; stopped? (state="+state+")");
        }
        
        driver.reload();
    }
 
    public boolean isSticky() {
        return getConfig(STICKY);
    } 
    
    @Override   
    public void connectSensors() {
        super.connectSensors();
        
        ConfigToAttributes.apply(this);
        String accessibleRootUrl = inferUrl(true);

        // "up" is defined as returning a valid HTTP response from nginx (including a 404 etc)
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(getConfig(HTTP_POLL_PERIOD))
                .baseUri(accessibleRootUrl)
                .baseUriVars(ImmutableMap.of("include-runtime","true"))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(new Function<HttpPollValue, Boolean>() {
                                @Override public Boolean apply(HttpPollValue input) {
                                    // Accept any nginx response (don't assert specific version), so that sub-classing
                                    // for a custom nginx build is not strict about custom version numbers in headers
                                    List<String> actual = input.getHeaderLists().get("Server");
                                    return actual != null && actual.size() == 1 && actual.get(0).startsWith("nginx");
                                }})
                        .onError(Functions.constant(false)))
                .build();

        // Can guarantee that parent/managementContext has been set
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings != null) {
            // Listen to the targets of each url-mapping changing
            subscribeToMembers(urlMappings, UrlMapping.TARGET_ADDRESSES, new SensorEventListener<Collection<String>>() {
                    @Override public void onEvent(SensorEvent<Collection<String>> event) {
                        update(); 
                    }});
            
            // Listen to url-mappings being added and removed
            AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy() {
                @Override protected void onEntityChange(Entity member) { update(); }
                @Override protected void onEntityAdded(Entity member) { update(); }
                @Override protected void onEntityRemoved(Entity member) { update(); }
            };
            addPolicy(policy);
            policy.setGroup(urlMappings);
        }
    }
    
    @Override
    public void stop() {
        // TODO Want http.poll to set SERVICE_UP to false on IOException. How?
        // And don't want stop to race with the last poll.
        super.stop();
        setAttribute(SERVICE_UP, false);
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
    }

    @Override
    public Class getDriverInterface() {
        return NginxDriver.class;
    }

    public void doExtraConfigurationDuringStart() {
        reconfigureService();
    }

    @Effector(description="Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    public String getCurrentConfiguration() {
        return getConfigFile();
    }
    
    @Override
    protected void reconfigureService() {

        String cfg = getConfigFile();
        if (cfg==null) return;
        
        if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, targetting {} and {}", new Object[] {this, serverPoolAddresses, findUrlMappings()});
        if (LOG.isTraceEnabled()) LOG.trace("Reconfiguring {}, config file:\n{}", this, cfg);
        
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (!driver.isCustomizationCompleted()) {
            if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, but driver's customization not yet complete so aborting");
            return;
        }
        
        driver.getMachine().copyTo(new ByteArrayInputStream(cfg.getBytes()), driver.getRunDir()+"/conf/server.conf");
        
        installSslKeys("global", getConfig(SSL_CONFIG));
        
        for (UrlMapping mapping: findUrlMappings()) {
            //cache ensures only the first is installed, which is what is assumed below
            installSslKeys(mapping.getDomain(), mapping.getConfig(UrlMapping.SSL_CONFIG));
        }
    }
    
    private final Set<String> installedKeysCache = Sets.newLinkedHashSet();

    /** installs SSL keys named as  ID.{crt,key}  where nginx can find them;
     * currently skips re-installs (does not support changing)
     */
    protected void installSslKeys(String id, ProxySslConfig ssl) {
        if (ssl == null) return;

        if (installedKeysCache.contains(id)) return;

        NginxSshDriver driver = (NginxSshDriver) getDriver();

        if (!Strings.isEmpty(ssl.getCertificateSourceUrl())) {
            String certificateDestination = Strings.isEmpty(ssl.getCertificateDestination()) ? driver.getRunDir() + "/conf/" + id + ".crt" : ssl.getCertificateDestination();
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0400"),
                    new ResourceUtils(this).getResourceFromUrl(ssl.getCertificateSourceUrl()),
                    certificateDestination);
        }

        if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
            String keyDestination = Strings.isEmpty(ssl.getKeyDestination()) ? driver.getRunDir() + "/conf/" + id + ".key" : ssl.getKeyDestination();
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0400"),
                    new ResourceUtils(this).getResourceFromUrl(ssl.getKeySourceUrl()),
                    keyDestination);
        }

        installedKeysCache.add(id);
    }

    public String getConfigFile() {
        // TODO should refactor this method to a new class with methods e.g. NginxConfigFileGenerator...
        
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) {
            if (LOG.isDebugEnabled()) LOG.debug("No driver for {}, so not generating config file (is entity stopping? state={})", 
                this, getAttribute(NginxController.SERVICE_STATE));
            return null;
        }
        
        StringBuilder config = new StringBuilder();
        config.append("\n");
        config.append(format("pid %s/logs/nginx.pid;\n",driver.getRunDir()));
        config.append("events {\n");
        config.append("  worker_connections 8196;\n");
        config.append("}\n");
        config.append("http {\n");
        
        ProxySslConfig globalSslConfig = getConfig(SSL_CONFIG);
        boolean ssl = globalSslConfig != null;

        if (ssl) {
            verifyConfig(globalSslConfig);
            appendSslConfig("global", config, "    ", globalSslConfig, true, true);
        };
        
        // If no servers, then defaults to returning 404
        // TODO Give nicer page back 
        if (getDomain()!=null || serverPoolAddresses==null || serverPoolAddresses.isEmpty()) {
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+getPort()+";\n");
            config.append(getCodeFor404());
            config.append("  }\n");
        }
        
        // For basic round-robin across the server-pool
        if (serverPoolAddresses != null && serverPoolAddresses.size() > 0) {
            config.append(format("  upstream "+getId()+" {\n"));
            if (isSticky()){
                config.append("    sticky;\n");
            }
            for (String address: serverPoolAddresses){
                config.append("    server "+address+";\n");
            }
            config.append("  }\n");
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+getPort()+";\n");
            if (getDomain()!=null)
                config.append("    server_name "+getDomain()+";\n");
            config.append("    location / {\n");
            config.append("      proxy_pass "+(globalSslConfig != null && globalSslConfig.getTargetIsSsl() ? "https" : "http")+"://"+getId()+";\n");
            config.append("    }\n");
            config.append("  }\n");
        }
        
        // For mapping by URL
        Iterable<UrlMapping> mappings = findUrlMappings();
        Multimap<String, UrlMapping> mappingsByDomain = LinkedHashMultimap.create();
        for (UrlMapping mapping : mappings) {
            Collection<String> addrs = mapping.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                mappingsByDomain.put(mapping.getDomain(), mapping);
            }
        }
        
        for (UrlMapping um : mappings) {
            Collection<String> addrs = um.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                String location = um.getPath() != null ? um.getPath() : "/";
                config.append(format("  upstream "+um.getUniqueLabel()+" {\n"));
                if (isSticky()){
                    config.append("    sticky;\n");
                }
                for (String address: addrs) {
                    config.append("    server "+address+";\n");
                }
                config.append("  }\n");
            }
        }
        
        for (String domain : mappingsByDomain.keySet()) {
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+getPort()+";\n");
            config.append("    server_name "+domain+";\n");
            boolean hasRoot = false;

            // set up SSL
            ProxySslConfig localSslConfig = null;
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                ProxySslConfig sslConfig = mappingInDomain.getConfig(UrlMapping.SSL_CONFIG);
                if (sslConfig!=null) {
                    verifyConfig(sslConfig);
                    if (localSslConfig!=null) {
                        if (localSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had already been provided by another mapping, ignoring this one",
                                    new Object[] {this, mappingInDomain, domain});
                        }
                    } else if (globalSslConfig!=null) {
                        if (globalSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had been provided at root nginx scope, ignoring this one",
                                    new Object[] {this, mappingInDomain, domain});
                        }
                    } else {
                        //new config, is okay
                        localSslConfig = sslConfig;
                    }
                }
            }
            boolean serverSsl;
            if (localSslConfig!=null) {
                serverSsl = appendSslConfig(""+domain, config, "    ", localSslConfig, true, true);
            } else if (globalSslConfig!=null) {
                // can't set ssl_certificate globally, so do it per server
                serverSsl = true; 
            }

            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                // TODO Currently only supports "~" for regex. Could add support for other options,
                // such as "~*", "^~", literals, etc.
                boolean isRoot = mappingInDomain.getPath()==null || mappingInDomain.getPath().length()==0 || mappingInDomain.getPath().equals("/");
                if (isRoot && hasRoot) {
                    LOG.warn(""+this+" mapping "+mappingInDomain+" provides a duplicate / proxy, ignoring");
                } else {
                    hasRoot |= isRoot;
                    String location = isRoot ? "/" : "~ " + mappingInDomain.getPath();
                    config.append("    location "+location+" {\n");
                    Collection<UrlRewriteRule> rewrites = mappingInDomain.getConfig(UrlMapping.REWRITES);
                    if (rewrites != null && rewrites.size() > 0) {
                        for (UrlRewriteRule rule: rewrites) {
                            config.append("      rewrite \"^"+rule.getFrom()+"$\" \""+rule.getTo()+"\"");
                            if (rule.isBreak()) config.append(" break");
                            config.append(" ;\n");
                        }
                    }
                    config.append("      proxy_pass "+
                        (localSslConfig != null && localSslConfig.getTargetIsSsl() ? "https" :
                         (localSslConfig == null && globalSslConfig != null && globalSslConfig.getTargetIsSsl()) ? "https" :
                         "http")+
                        "://"+mappingInDomain.getUniqueLabel()+" ;\n");
                    config.append("    }\n");
                }
            }
            if (!hasRoot) {
                //provide a root block giving 404 if there isn't one for this server
                config.append("    location / { \n"+getCodeFor404()+"    }\n");
            }
            config.append("  }\n");
        }
        
        config.append("}\n");

        return config.toString();
    }

    protected String getCodeForServerConfig() {
        // See http://wiki.nginx.org/HttpProxyModule
        return ""+
            // this prevents nginx from reporting version number on error pages
            "    server_tokens off;\n"+
            
            // this prevents nginx from using the internal proxy_pass codename as Host header passed upstream.
            // Not using $host, as that causes integration test to fail with a "connection refused" testing
            // url-mappings, at URL "http://localhost:${port}/atC0" (with a trailing slash it does work).
            "    proxy_set_header Host $http_host;\n"+
            
            // following added, as recommended for wordpress in:
            // http://zeroturnaround.com/labs/wordpress-protips-go-with-a-clustered-approach/#!/
            "    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n"+
            "    proxy_set_header X-Real-IP $remote_addr;\n";
    }
    
    protected String getCodeFor404() {
        return "    return 404;\n";
    }
    
    void verifyConfig(ProxySslConfig proxySslConfig) {
          if(Strings.isEmpty(proxySslConfig.getCertificateDestination()) && Strings.isEmpty(proxySslConfig.getCertificateSourceUrl())){
            throw new IllegalStateException("ProxySslConfig can't have a null certificateDestination and null certificateSourceUrl. One or both need to be set");
        }
    }

    public boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
                                   boolean sslBlock, boolean certificateBlock) {
        if (ssl == null) return false;
        if (sslBlock) {
            out.append(prefix);
            out.append("ssl on;\n");
        }
        if (ssl.getReuseSessions()) {
            out.append(prefix);
            out.append("proxy_ssl_session_reuse on;");
        }
        if (certificateBlock) {
            String cert;
            if (Strings.isEmpty(ssl.getCertificateDestination())) {
                cert = "" + id + ".crt";
            } else {
                cert = ssl.getCertificateDestination();
            }

            out.append(prefix);
            out.append("ssl_certificate " + cert + ";\n");

            String key;
            if (!Strings.isEmpty(ssl.getKeyDestination())) {
                key = ssl.getKeyDestination();
            } else if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
                key = "" + id + ".key";
            } else {
                key = null;
            }

            if (key != null) {
                out.append(prefix);
                out.append("ssl_certificate_key " + key + ";\n");
            }
        }
        return true;
    }

    protected Iterable<UrlMapping> findUrlMappings() {
        // For mapping by URL
        Group urlMappingGroup = getConfig(URL_MAPPINGS);
        if (urlMappingGroup != null) {
            return Iterables.filter(urlMappingGroup.getMembers(), UrlMapping.class);
        } else {
            return Collections.<UrlMapping>emptyList();
        }
    }

    @Override
    public String getShortName() {
        return "Nginx";
    }
}
