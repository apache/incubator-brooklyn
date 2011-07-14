package brooklyn.entity.proxy.nginx

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity;
import brooklyn.entity.group.AbstractController
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
    public NginxController(Map properties=[:], Entity owner=null) {
        super(properties, owner)
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
        addresses.collect servers, { InetAddress host, portList ->
                portList.collect servers, { int port -> host.hostAddress + ":" + port } }
        StringBuffer config = []
        config.append """
pid ${setup.runDir}/pid.txt;
events {
  worker_connections 8196;
}
http {
  upstream ${id} {
    ip_hash;
"""
        servers.each { config.append("  server ${it};\n") }
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
