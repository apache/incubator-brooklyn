package brooklyn.entity.proxy.nginx;

import java.util.concurrent.TimeUnit;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.group.AbstractController;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.adapter.ConfigSensorAdapter;
import brooklyn.event.adapter.HttpSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import static java.lang.String.format;

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
        // block until we have targets
        execution.submit(DependentConfiguration.attributeWhenReady(this, TARGETS)).get();
    }
    
    protected void reconfigureService() {
        LOG.info("Reconfiguring "+getDisplayName()+", members are "+addresses);
        NginxSshDriver driver = (NginxSshDriver)getDriver();

        File file = new File("/tmp/"+getId());
        Files.write(getConfigFile(), file, Charsets.UTF_8);
        driver.machine.copyTo(file, driver.getRunDir()+"/conf/server.conf");
        file.delete();
    }

    public String getConfigFile() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        StringBuffer config = new StringBuffer();
        config.append("\n")
        config.append(format("pid %s/logs/nginx.pid;\n",driver.getRunDir()));
        config.append("events {\n");
        config.append("  worker_connections 8196;\n");
        config.append("}\n");
        config.append("http {\n");
        config.append(format("  upstream "+getId()+" {\n"))
        if (sticky){
            config.append("        sticky;\n");
        }
        for (String address: addresses){
            config.append("    server "+address+";\n")
        }
        config.append("}\n")
        config.append("  server {\n");
        config.append("    listen "+getPort()+";\n")
        config.append("    server_name "+getDomain()+";\n")
        config.append("    location / {\n");
        config.append("      proxy_pass http://"+getId()+"\n;");
        config.append("    }\n");
        config.append("  }\n");
        config.append("}\n");

        return config.toString();
    }
}
