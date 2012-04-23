package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;

public class LocalhostResolver implements LocationResolver {
    
    public static final String LOCALHOST = "localhost";
    
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        return newLocation();
    }
    
    public LocalhostMachineProvisioningLocation newLocation() {
        return new LocalhostMachineProvisioningLocation();
    }
    
    @Override
    public String getPrefix() {
        return LOCALHOST;
    }
    
}
