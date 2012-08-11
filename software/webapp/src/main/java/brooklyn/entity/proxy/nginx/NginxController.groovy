package brooklyn.entity.proxy.nginx;

import static java.lang.String.format

import java.util.concurrent.TimeUnit

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.AbstractController
import brooklyn.entity.webapp.WebAppService
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Predicates
import com.google.common.collect.HashMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Multimap

/**
 * An entity that represents an Nginx proxy controlling a cluster.
 * <p>
 * The default driver *builds* nginx from source (because binaries are not reliably available, esp not with sticky sessions).
 * This requires gcc and other build tools installed. The code attempts to install them but inevitably 
 * this entity may be more finicky about the OS/image where it runs than others.
 * <p>
 * Paritcularly on OS X we require Xcode and command-line gcc installed and on the path.
 */
public class NginxController extends AbstractController {

    static { TimeExtras.init(); }
       
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
        new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "1.3.0");

    @SetFromFlag("sticky")
    public static final BasicConfigKey<Boolean> STICKY =
        new BasicConfigKey<Boolean>(Boolean.class, "nginx.sticky", "whether to use sticky sessions", true);

    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;
    
    public NginxController(Entity owner) {
        this(new LinkedHashMap(), owner);
    }

    public NginxController(Map properties){
        this(properties,null);
    }

    public NginxController(Map properties, Entity owner) {
        super(properties, owner);
        subscribeToChildren(this, UrlMapping.TARGET_ADDRESSES, { update(); } as SensorEventListener);
    }

    @Override
    public void reload() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) throw new IllegalStateException("Cannot reload (no driver instance; stopped?)");
        
        driver.reload();
    }
 
    public boolean isSticky() {
        return getConfig(STICKY);
    } 
    
    @Override   
    public void connectSensors() {
        super.connectSensors();
        
        makeUrl();
        
        sensorRegistry.register(new ConfigSensorAdapter());
        
        HttpSensorAdapter http = sensorRegistry.register(
            new HttpSensorAdapter(getAttribute(AbstractController.SPECIFIED_URL), 
                period: 1000*TimeUnit.MILLISECONDS));
        
        // "up" is defined as returning a valid HTTP response from nginx (including a 404 etc)
        http.with {
            poll(SERVICE_UP, {
                headerLists.get("Server") == ["nginx/"+getConfig(SUGGESTED_VERSION)] 
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
    
    protected void makeUrl() {
        super.makeUrl();
        setAttribute(ROOT_URL, url);
    }
    
    public NginxSshDriver newDriver(SshMachineLocation machine) {
        return new NginxSshDriver(this, machine);
    }

    public void doExtraConfigurationDuringStart() {
        reconfigureService();
    }
    
    protected void preStart() {
        super.preStart();
    }
    
    protected void reconfigureService() {

        def cfg = getConfigFile();
        if (cfg==null) return;
        LOG.info("Reconfiguring "+this+", targetting "+addresses+" and "+getOwnedChildren());
        
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (!driver.isCustomizationCompleted()) return;
        driver.machine.copyTo(new ByteArrayInputStream(cfg.getBytes()), driver.getRunDir()+"/conf/server.conf");
    }

    public String getConfigFile() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) return null;
                
        StringBuilder config = new StringBuilder();
        config.append("\n")
        config.append(format("pid %s/logs/nginx.pid;\n",driver.getRunDir()));
        config.append("events {\n");
        config.append("  worker_connections 8196;\n");
        config.append("}\n");
        config.append("http {\n");
        
        // For basic round-robin across the cluster
        if (addresses) {
            config.append(format("  upstream "+getId()+" {\n"))
            if (sticky){
                config.append("    sticky;\n");
            }
            for (String address: addresses){
                config.append("    server "+address+";\n")
            }
            config.append("  }\n")
            config.append("  server {\n");
            config.append("    listen "+getPort()+";\n")
            config.append("    server_name "+getDomain()+";\n")
            config.append("    location / {\n");
            config.append("      proxy_pass http://"+getId()+"\n;");
            config.append("    }\n");
            config.append("  }\n");
        }
        
        // For mapping by URL
        Iterable<UrlMapping> mappings = Iterables.filter(ownedChildren, Predicates.instanceOf(UrlMapping.class));
        Multimap<String, UrlMapping> mappingsByDomain = new HashMultimap<String, UrlMapping>();
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
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                String location = mappingInDomain.getPath() != null ? mappingInDomain.getPath() : "/";
                config.append("    location "+location+" {\n");
                config.append("      proxy_pass http://"+mappingInDomain.uniqueLabel+"\n;");
                config.append("    }\n");
            }
            config.append("  }\n");
        }
        
        // If no servers, then defaults to returning 404
        // TODO Give nicer page back 
        config.append("  server {\n");
        config.append("    listen "+getPort()+";\n")
        config.append("    return 404;\n")
        config.append("  }\n");
        
        config.append("}\n");

        return config.toString();
    }
}
