package brooklyn.location.basic.jclouds

import java.util.Map

import org.jclouds.Constants

class JcloudsLocationFactory {

    // FIXME streetAddress is temporary, until we get lat-lon working in google maps properly
    
    private static final Map locationSpecificConf = [:]
    private final Map conf = [:]
    
    public JcloudsLocationFactory(Map conf) {
        this.conf = [:]
        this.conf << conf
    }
    
    public JcloudsLocationFactory(String identity, String credential) {
        this([identity:identity, credential:credential])
    }

    public JcloudsLocation newLocation(String provider, String locationId) {
//        if (!locationSpecificConf.containsKey(locationId)) {
//            throw new IllegalArgumentException("Unknown location $locationId");
//        }
        Map allconf = [:]
        allconf << conf
        allconf.provider = provider
        allconf.providerLocationId = locationId
        allconf << (locationSpecificConf.get(locationId) ?: [:])
        return new JcloudsLocation(allconf);
    }
}
