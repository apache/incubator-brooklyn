package brooklyn.extras.cloudfoundry

import java.util.Map;

import com.google.common.base.Preconditions;

import brooklyn.location.basic.AbstractLocation;

/** currently only supports cloudfoundry.com */
class CloudFoundryLocation extends AbstractLocation {

    public CloudFoundryLocation(Map properties = [:]) {
        super(properties);
    }
    
}
