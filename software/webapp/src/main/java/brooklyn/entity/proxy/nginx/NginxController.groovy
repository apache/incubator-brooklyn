package brooklyn.entity.proxy.nginx

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.entity.group.AbstractController
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldHttpSensorAdapter
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation

import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * An entity that represents an Nginx proxy controlling a cluster.
 */
public class NginxController extends AbstractController {
    transient OldHttpSensorAdapter httpAdapter

    public NginxController(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    protected void connectSensors() {
		super.connectSensors();
        httpAdapter = new OldHttpSensorAdapter(this)
        sensorRegistry.addSensor(SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }
    
    private boolean computeNodeUp() {
        String url = getAttribute(AbstractController.URL)
        ValueProvider<String> provider = httpAdapter.newHeaderValueProvider(url, "Server")
        try {
            String productVersion = provider.compute()
	        return (productVersion == "nginx/" + getAttribute(Attributes.VERSION))
        } catch (IOException ioe) {
            return false
        }
    }

    public SshBasedAppSetup newDriver(SshMachineLocation machine) {
        return NginxSetup.newInstance(this, machine)
    }

    public void doExtraConfigurationDuringStart() {
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
