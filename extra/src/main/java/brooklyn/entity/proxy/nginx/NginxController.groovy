package brooklyn.entity.proxy.nginx

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.group.AbstractController
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * An entity that represents an Nginx proxy controlling a cluster.
 */
public class NginxController extends AbstractController {
    transient HttpSensorAdapter httpAdapter

    public NginxController(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    protected void initSensors() {
        super.initSensors()
        httpAdapter = new HttpSensorAdapter(this)
        attributePoller.addSensor(SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }
    
    @Override
    public void stop() {
        attributePoller.close()
        super.stop()
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

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return NginxSetup.newInstance(this, machine)
    }

    @Override
    public void configure() {
        MachineLocation machine = locations.first()
        File file = new File("/tmp/${id}")
        Files.write(getConfigFile(), file, Charsets.UTF_8)
		setup.machine.copyTo file, "${setup.runDir}/conf/server.conf"
        file.delete()
    }

    public String getConfigFile() {
        List<String> servers = []
        addresses.each { InetAddress host, portList -> portList.collect(servers, { int port -> host.hostAddress + ":" + port }) }
        StringBuffer config = []
        config.append """
pid ${setup.runDir}/logs/nginx.pid;
events {
  worker_connections 8196;
}
http {
  upstream ${id} {
    sticky;
"""
        servers.each { String address -> config.append("    server ${address};\n") }
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
