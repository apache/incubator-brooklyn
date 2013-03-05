package brooklyn.location.access;

import com.google.common.base.Objects;

import brooklyn.location.Location;

public class PortMapping {
    
    public PortMapping(String publicIpId, int publicPort, Location target, int privatePort) {
        super();
        this.publicIpId = publicIpId;
        this.publicPort = publicPort;
        this.target = target;
        this.privatePort = privatePort;
    }

    final String publicIpId;
    final int publicPort;

    final Location target;
    final int privatePort;
    // CIDR's ?

    public int getPublicPort() {
        return publicPort;
    }

    public Location getTarget() {
        return target;
    }
    public int getPrivatePort() {
        return privatePort;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("public=", publicPort).
            add("private", target+":"+privatePort).toString();
    }
}