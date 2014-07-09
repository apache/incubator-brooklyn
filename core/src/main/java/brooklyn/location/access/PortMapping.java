package brooklyn.location.access;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

import brooklyn.location.Location;

public class PortMapping {
    
    public PortMapping(String publicIpId, int publicPort, Location target, int privatePort) {
        super();
        this.publicIpId = checkNotNull(publicIpId, "publicIpId");
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
        return Objects.toStringHelper(this).add("public", publicIpId+":"+publicPort).
                add("private", target+":"+privatePort).toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PortMapping)) return false;
        PortMapping opm = (PortMapping)obj;
        return Objects.equal(publicIpId, opm.publicIpId) &&
            Objects.equal(publicPort, opm.publicPort) &&
            Objects.equal(target, opm.target) &&
            Objects.equal(privatePort, opm.privatePort);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(publicIpId, publicPort, target, privatePort);
    }
    
}