package brooklyn.location.access;

import com.google.common.base.Objects;

import brooklyn.location.Location;

public class PortMapping {
    String publicIpId;
    int publicPort;

    Location target;
    int privatePort;
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
            add("private=", target+":"+privatePort).toString();
    }
}