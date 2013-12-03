package brooklyn.location.jclouds.networking;

import org.jclouds.compute.domain.NodeMetadata;

import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;

public interface JcloudsPortForwarderExtension {

    public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr);
}
