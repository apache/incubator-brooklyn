package brooklyn.location.jclouds.networking;

import org.jclouds.compute.domain.NodeMetadata;

import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

public class JcloudsPortForwarderExtensionImpl implements JcloudsPortForwarderExtension {

    @Override
    public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
        String vmIp = Iterables.get(Iterables.concat(node.getPrivateAddresses(), node.getPublicAddresses()), 0);
        HostAndPort targetSide = HostAndPort.fromParts(vmIp, node.getLoginPort());
        throw new UnsupportedOperationException();
//        sshHostAndPort = Optional.of(portForwarder.openPortForwarding(
//                targetSide, 
//                Optional.<Integer>absent(), 
//                Protocol.TCP, 
//                Cidr.UNIVERSAL));
    }
}
