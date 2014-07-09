package brooklyn.location.jclouds;

import java.util.Map;

import org.jclouds.compute.domain.NodeMetadata;

import brooklyn.location.basic.SupportsPortForwarding.RequiresPortForwarding;

public abstract class AbstractJcloudsSubnetSshMachineLocation extends JcloudsSshMachineLocation implements RequiresPortForwarding {

    public AbstractJcloudsSubnetSshMachineLocation() {
    }

    /** @deprecated since 0.6.0 use no-arg constructor, as per parent */
    @Deprecated
    public AbstractJcloudsSubnetSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
        super(flags, parent, node);
    }

}
