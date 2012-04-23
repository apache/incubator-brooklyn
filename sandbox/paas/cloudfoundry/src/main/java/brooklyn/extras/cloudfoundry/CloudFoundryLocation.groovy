package brooklyn.extras.cloudfoundry

import java.net.InetAddress;
import java.util.Map

import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.entity.webapp.ElasticJavaWebAppService.ElasticJavaWebAppServiceAwareLocation
import brooklyn.location.AddressableLocation
import brooklyn.location.Location
import brooklyn.location.LocationResolver
import brooklyn.location.basic.AbstractLocation
import brooklyn.util.flags.SetFromFlag


/** defines a cloudfoundry location
 * <p>
 * this can be specified as 'cloudfoundry:api.cloudfoundry.com',
 * or just 'cloudfoundry' (to use the default `vmc target`, in ~/.vmc_target)
 * <p>
 * username+password are not currently specifiable; 
 * we assume a token has been set up via `vmc login` (stored in ~/.vmc_token) */
class CloudFoundryLocation extends AbstractLocation implements AddressableLocation, ElasticJavaWebAppServiceAwareLocation {

    @SetFromFlag
    private String target;
    
    public CloudFoundryLocation(Map properties = [:]) {
        super(properties);
        if (!target) target="api.cloudfoundry.com";
        if (!name) name="Cloud Foundry ("+target+")";
    }
    
    public static class Resolver implements LocationResolver {
        @Override
        public String getPrefix() {
            return "cloudfoundry";
        }

        @Override
        public Location newLocationFromString(Map properties, String spec) {
            if (spec.equals(getPrefix()))
                return new CloudFoundryLocation();
            String target = spec.substring(spec.indexOf(':')+1);
            // target endpoint is allowed to be specified here as second part of spec
            // default of null means to use whatever vmc is configured with
            return new CloudFoundryLocation(target: target);
        }
    }

    @Override
    public ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory() {
        return new CloudFoundryJavaWebAppCluster.Factory();
    }

    public String getTarget() {
        return this.@target;
    }

    @Override
    public InetAddress getAddress() {
        if (!target) return null;
        return InetAddress.getByName(target);
    }
        
}
