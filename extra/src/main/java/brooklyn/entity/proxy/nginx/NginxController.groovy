package brooklyn.entity.proxy.nginx

import java.util.Collection
import java.util.Map

import brooklyn.entity.group.AbstractController
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * An entity that represents an NIGINX proxy controlling a cluster.
 */
public class NginxController extends AbstractController {
    public NginxController(Map properties=[:]) {
        super(properties);
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return NginxSetup.newInstance(this, machine)
    }

    @Override
    public void start(Collection<Location> locations) {
        configure()

        super.start locations
	}

    @Override
    public void stop() {
	}

    @Override
    public void restart() {
	}

    public void configure() {
        File file = new File("/tmp/${id}")
        Files.write(getConfigFile(), file, Charsets.UTF_8)
        locations.each { SshMachineLocation machine -> machine.copyTo file, "${machine.setup.runDir}/conf/server.conf" }
        restart()
    }

    public String getConfigFile() {
        List<String> servers = []
        addresses.collect servers, { InetAddress host, portList ->
                portList.collect servers, { int port -> host.hostAddress + ":" + port } }

        StringBuffer config = []
        config.append("upstream backend  \n")
        servers.each { config.append("    server ${it};\n") }
        config.append("}\n")
        config.append """
server {
  location / {
    proxy_pass ${url};
  }
}
        """
        config.toString()
    }
}
