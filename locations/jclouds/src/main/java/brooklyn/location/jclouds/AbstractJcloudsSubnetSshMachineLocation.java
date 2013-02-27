package brooklyn.location.jclouds;

import java.util.Map;

import org.jclouds.compute.domain.NodeMetadata;

import com.google.common.net.HostAndPort;

import brooklyn.location.basic.SupportsPortForwarding.RequiresPortForwarding;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

public abstract class AbstractJcloudsSubnetSshMachineLocation extends JcloudsSshMachineLocation implements RequiresPortForwarding {

    public AbstractJcloudsSubnetSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
        super(flags, parent, node);
    }

}
