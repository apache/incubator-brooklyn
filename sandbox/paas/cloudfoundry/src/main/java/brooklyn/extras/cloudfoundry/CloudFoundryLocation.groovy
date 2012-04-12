package brooklyn.extras.cloudfoundry

import java.util.Map

import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.webapp.ElasticJavaWebAppService;
import brooklyn.entity.webapp.ElasticJavaWebAppService.ElasticJavaWebAppServiceAwareLocation;
import brooklyn.location.Location
import brooklyn.location.LocationResolver
import brooklyn.location.basic.AbstractLocation

/** currently only supports cloudfoundry.com */
class CloudFoundryLocation extends AbstractLocation implements ElasticJavaWebAppServiceAwareLocation {

    public CloudFoundryLocation(Map properties = [:]) {
        super(properties);
    }
    
    public static class Resolver implements LocationResolver {
        @Override
        public String getPrefix() {
            return "cloudfoundry";
        }

        @Override
        public Location newLocationFromString(Map properties, String spec) {
            assert spec.equals(getPrefix()) : "location '"+getPrefix()+"' is not currently parametrisable (invalid '"+spec+"')"
            // TODO target endpoint (default api.cloudfoundry.com) could be specified here as second part of spec?
            return new CloudFoundryLocation();
        }
    }

    @Override
    public ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory() {
        return new CloudFoundryJavaWebAppCluster.Factory();
    }
    
}
