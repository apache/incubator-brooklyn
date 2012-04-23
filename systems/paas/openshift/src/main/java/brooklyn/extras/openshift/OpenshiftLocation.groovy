package brooklyn.extras.openshift

import java.util.Map;

import com.google.common.base.Preconditions;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.location.basic.AbstractLocation;

class OpenshiftLocation extends AbstractLocation {

    public OpenshiftLocation(Map properties = [:]) {
        super(properties);
        setProperties(leftoverProperties);
    }
    
    String url = "https://openshift.redhat.com/broker"
    String username, password
    
    protected void setProperties(Map properties) {
        if (properties.url) {
            Preconditions.checkArgument properties.url instanceof String, "'url' property should be a string"
            url = properties.remove("url")
        }
        if (properties.username) {
            Preconditions.checkArgument properties.username instanceof String, "'username' property should be a string"
            username = properties.remove("username")
        }
        if (properties.password) {
            Preconditions.checkArgument properties.password instanceof String, "'password' property should be a string"
            password = properties.remove("password")
        }
    }
    
    public static class Resolver implements LocationResolver {
        @Override
        public String getPrefix() {
            return "openshift";
        }

        @Override
        public Location newLocationFromString(Map properties, String spec) {
            assert spec.equals(getPrefix()) : "location '"+getPrefix()+"' is not currently parametrisable (invalid '"+spec+"')"
            //TODO could support multiple URL/endpoints?
            return new OpenshiftLocation(properties);
        }
    }
    
}
