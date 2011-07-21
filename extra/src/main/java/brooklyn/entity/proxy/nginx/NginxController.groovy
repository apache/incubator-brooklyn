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
 * An entity that represents an NIGINX proxy controlling a cluster.
 */
public class NginxController extends AbstractController {
    transient HttpSensorAdapter httpAdapter
    transient AttributePoller attributePoller

    public NginxController(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void start(List<Location> locations) {
        super.start(locations)

        httpAdapter = new HttpSensorAdapter(this)
        attributePoller = new AttributePoller(this)
        initHttpSensors()
    }

    @Override
    public void stop() {
        attributePoller.close()
        super.stop()
    }

    public void initHttpSensors() {
        attributePoller.addSensor(SERVICE_UP, { computeNodeUp() } as ValueProvider)
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

    public synchronized void configure() {
        MachineLocation machine = locations.first()
        SshBasedAppSetup setup = getSshBasedSetup(machine)
        File file = new File("/tmp/${id}")
        file.deleteOnExit()
        Files.write(getConfigFile(setup), file, Charsets.UTF_8)
		setup.machine.copyTo file, "${setup.runDir}/conf/server.conf"
    }

    public String getConfigFile(SshBasedAppSetup setup) {
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
