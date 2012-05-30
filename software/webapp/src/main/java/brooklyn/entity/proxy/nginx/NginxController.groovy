package brooklyn.entity.proxy.nginx

import java.util.Map
import java.util.concurrent.TimeUnit

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopDriver
import brooklyn.entity.group.AbstractController
import brooklyn.entity.webapp.WebAppService
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.HttpSensorAdapter;
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldHttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * An entity that represents an Nginx proxy controlling a cluster.
 */
public class NginxController extends AbstractController {

    static { TimeExtras.init() }
       
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "1.2.0" ]

    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;
    
    transient OldHttpSensorAdapter httpAdapter

    public NginxController(Entity owner) { this([:], owner) }
    public NginxController(Map properties=[:], Entity owner=null) {
        super(properties, owner)
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
        super.stop()
        setAttribute(SERVICE_UP, false)
    }
    
    protected void makeUrl() {
        super.makeUrl();
        setAttribute(ROOT_URL, url);
    }
    
    public StartStopDriver newDriver(SshMachineLocation machine) {
        return new NginxSshDriver(this, machine)
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
        LOG.info("Reconfiguring $displayName, members are $addresses")
        
        MachineLocation machine = firstLocation()
        File file = new File("/tmp/${id}")
        Files.write(getConfigFile(), file, Charsets.UTF_8)
		driver.machine.copyTo file, "${driver.runDir}/conf/server.conf"
        file.delete()
    }

    public String getConfigFile() {
        StringBuffer config = []
        config.append """
pid ${driver.runDir}/logs/nginx.pid;
events {
  worker_connections 8196;
}
http {
  upstream ${id} {
    sticky;
"""
        addresses.each { String address -> config.append("    server ${address};\n") }
        config.append """
  }
  server {
    listen ${port};
    server_name ${domain};
    location / {
      proxy_pass http://${id};
    }
  }
}
"""
        config.toString()
    }
}
