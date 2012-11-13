package brooklyn.entity.proxy.nginx;

import static java.lang.String.format

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.entity.proxy.AbstractController
import brooklyn.entity.proxy.ProxySslConfig
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.ResourceUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.TimeExtras
import brooklyn.util.text.Strings

import com.google.common.collect.Iterables
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap

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
public class NginxController extends AbstractController {

    private static final Logger LOG = LoggerFactory.getLogger(NginxController.class);
    static { TimeExtras.init(); }
    
    public static final MethodEffector<Void> GET_CURRENT_CONFIGURATION = 
            new MethodEffector<Void>(NginxController.class, "getCurrentConfiguration");
    
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
        new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "1.3.7");

    @SetFromFlag("sticky")
    public static final BasicConfigKey<Boolean> STICKY =
        new BasicConfigKey<Boolean>(Boolean.class, "nginx.sticky", "whether to use sticky sessions", true);

    @SetFromFlag("httpPollPeriod")
    public static final BasicConfigKey<Long> HTTP_POLL_PERIOD =
        new BasicConfigKey<Long>(Long.class, "nginx.sensorpoll.http", "poll period (in milliseconds)", 1000);

    public NginxController(Entity owner) {
        this(new LinkedHashMap(), owner);
    }

    public NginxController(Map properties){
        this(properties,null);
    }

    public NginxController(Map properties, Entity owner) {
        super(properties, owner);
    }

    //public void onManagementBecomingMaster() {
    @Override
    protected void postStart() {
        super.postStart();
        
        // Now can guarantee that owner/managementContext has been set
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings != null) {
            // Listen to the targets of each url-mapping changing
            subscribeToMembers(urlMappings, UrlMapping.TARGET_ADDRESSES, { update(); } as SensorEventListener);
            
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
        
        sensorRegistry.register(new ConfigSensorAdapter());
        
        HttpSensorAdapter http = sensorRegistry.register(
            new HttpSensorAdapter(getAttribute(AbstractController.ROOT_URL), 
                period: getConfig(HTTP_POLL_PERIOD)*TimeUnit.MILLISECONDS));
        
        // "up" is defined as returning a valid HTTP response from nginx (including a 404 etc)
        http.with {
            poll(SERVICE_UP, {
                // Accept any nginx response (don't assert specific version), so that sub-classing
                // for a custom nginx build is not strict about custom version numbers in headers
                def actual = headerLists.get("Server")
                return actual != null && actual.size() == 1 && actual.get(0).startsWith("nginx/"); 
            })
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
    public Class getDriverInterface() {
        return NginxDriver.class;
    }

    public void doExtraConfigurationDuringStart() {
        reconfigureService();
    }

    @Description("Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    public String getCurrentConfiguration() {
        return getConfigFile();
    }
    
    @Override
    protected void reconfigureService() {

        String cfg = getConfigFile();
        if (cfg==null) return;
        
        if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, targetting {} and {}", this, serverPoolAddresses, findUrlMappings());
        if (LOG.isTraceEnabled()) LOG.trace("Reconfiguring {}, config file:\n{}", this, cfg);
        
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (!driver.isCustomizationCompleted()) {
            if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, but driver's customization not yet complete so aborting");
            return;
        }
        
        driver.machine.copyTo(new ByteArrayInputStream(cfg.getBytes()), driver.getRunDir()+"/conf/server.conf");
        
        installSslKeys("global", getConfig(SSL_CONFIG));
        
        for (UrlMapping mapping: findUrlMappings()) {
            //cache ensures only the first is installed, which is what is assumed below
            installSslKeys(mapping.getDomain(), mapping.getConfig(UrlMapping.SSL_CONFIG));
        }
    }
    
    Set<String> installedKeysCache = [];

    /** installs SSL keys named as  ID.{crt,key}  where nginx can find them;
     * currently skips re-installs (does not support changing)
     */
    protected void installSslKeys(String id, ProxySslConfig ssl) {
        if (ssl == null) return;

        if (installedKeysCache.contains(id)) return;

        NginxSshDriver driver = (NginxSshDriver) getDriver();

        if (!Strings.isEmpty(ssl.certificateSourceUrl)) {
            String certificateDestination = Strings.isEmpty(ssl.certificateDestination) ? driver.getRunDir() + "/conf/" + id + ".crt" : ssl.certificateDestination;
            driver.machine.copyTo(permissions: "0400",
                    new ResourceUtils(this).getResourceFromUrl(ssl.certificateSourceUrl),
                    certificateDestination);
        }

        if (!Strings.isEmpty(ssl.keySourceUrl)) {
            String keyDestination = Strings.isEmpty(ssl.keyDestination) ? driver.getRunDir() + "/conf/" + id + ".key" : ssl.keyDestination;
            driver.machine.copyTo(permissions: "0400",
                    new ResourceUtils(this).getResourceFromUrl(ssl.keySourceUrl),
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
        config.append("\n")
        config.append(format("pid %s/logs/nginx.pid;\n",driver.getRunDir()));
        config.append("events {\n");
        config.append("  worker_connections 8196;\n");
        config.append("}\n");
        config.append("http {\n");
        
        ProxySslConfig globalSslConfig = getConfig(SSL_CONFIG);
        boolean ssl = globalSslConfig != null;

        if (ssl) {
            verifyConfig(globalSslConfig)
            appendSslConfig("global", config, "    ", globalSslConfig, true, true)
        };
        
        // If no servers, then defaults to returning 404
        // TODO Give nicer page back 
        if (getDomain()!=null || serverPoolAddresses==null || serverPoolAddresses.isEmpty()) {
            config.append("  server {\n");
            config.append("    listen "+getPort()+";\n")
            config.append("    return 404;\n")
            config.append("  }\n");
        }
        
        // For basic round-robin across the server-pool
        if (serverPoolAddresses) {
            config.append(format("  upstream "+getId()+" {\n"))
            if (sticky){
                config.append("    sticky;\n");
            }
            for (String address: serverPoolAddresses){
                config.append("    server "+address+";\n")
            }
            config.append("  }\n")
            config.append("  server {\n");
            config.append("    listen "+getPort()+";\n")
            if (getDomain()!=null)
                config.append("    server_name "+getDomain()+";\n")
            config.append("    location / {\n");
            config.append("      proxy_pass "+(globalSslConfig && globalSslConfig.targetIsSsl ? "https" : "http")+"://"+getId()+";\n");
            config.append("    }\n");
            config.append("  }\n");
        }
        
        // For mapping by URL
        Iterable<UrlMapping> mappings = findUrlMappings();
        Multimap<String, UrlMapping> mappingsByDomain = LinkedHashMultimap.create();
        for (UrlMapping mapping : mappings) {
            Collection<String> addrs = mapping.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs) {
                mappingsByDomain.put(mapping.domain, mapping);
            }
        }
        
        for (UrlMapping um : mappings) {
            Collection<String> addrs = um.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs) {
                String location = um.getPath() != null ? um.getPath() : "/";
                config.append(format("  upstream "+um.uniqueLabel+" {\n"))
                if (sticky){
                    config.append("    sticky;\n");
                }
                for (String address: addrs) {
                    config.append("    server "+address+";\n")
                }
                config.append("  }\n")
            }
        }
        
        for (String domain : mappingsByDomain.keySet()) {
            config.append("  server {\n");
            config.append("    listen "+getPort()+";\n")
            config.append("    server_name "+domain+";\n")
            boolean hasRoot = false;

            // set up SSL
            ProxySslConfig localSslConfig = null;
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                ProxySslConfig sslConfig = mappingInDomain.getConfig(UrlMapping.SSL_CONFIG);
                if (sslConfig!=null) {
                    verifyConfig(sslConfig)
                    if (localSslConfig!=null) {
                        if (localSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn(""+this+" mapping "+mappingInDomain+" provides SSL config for "+domain+" when a different config had already been provided by another mapping, ignoring this one");
                        }
                    } else if (globalSslConfig!=null) {
                        if (globalSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn(""+this+" mapping "+mappingInDomain+" provides SSL config for "+domain+" when a different config had been provided at root nginx scope, ignoring this one");
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
                    if (rewrites) {
                        for (UrlRewriteRule rule: rewrites) {
                            config.append("      rewrite \"^"+rule.getFrom()+'$\" \"'+rule.getTo()+"\"");
                            if (rule.isBreak()) config.append(" break");
                            config.append(" ;\n");
                        }
                    }
                    config.append("      proxy_pass "+
                        (localSslConfig && localSslConfig.targetIsSsl ? "https" :
                         !localSslConfig && globalSslConfig && globalSslConfig.targetIsSsl ? "https" :
                         "http")+
                        "://"+mappingInDomain.uniqueLabel+" ;\n");
                    config.append("    }\n");
                }
            }
            if (!hasRoot) {
                //provide a root block giving 404 if there isn't one for this server
                config.append("    location / { return 404; }\n");
            }
            config.append("  }\n");
        }
        
        config.append("}\n");

        return config.toString();
    }

    void verifyConfig(ProxySslConfig proxySslConfig) {
          if(Strings.isEmpty(proxySslConfig.certificateDestination) && Strings.isEmpty(proxySslConfig.certificateSourceUrl)){
            throw new IllegalStateException("ProxySslConfig can't have a null certificateDestination and null certificateSourceUrl. One or both need to be set")
        }
    }

    public boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
                                   boolean sslBlock, boolean certificateBlock) {
        if (ssl == null) return false;
        if (sslBlock) {
            out.append(prefix);
            out.append("ssl on;\n");
        }
        if (ssl.reuseSessions) {
            out.append(prefix);
            out.append("proxy_ssl_session_reuse on;");
        }
        if (certificateBlock) {
            String cert;
            if (Strings.isEmpty(ssl.certificateDestination)) {
                cert = "" + id + ".crt";
            } else {
                cert = ssl.certificateDestination;
            }

            out.append(prefix);
            out.append("ssl_certificate " + cert + ";\n");

            String key;
            if (!Strings.isEmpty(ssl.keyDestination)) {
                key = ssl.keyDestination;
            } else if (!Strings.isEmpty(ssl.keySourceUrl)) {
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

}
