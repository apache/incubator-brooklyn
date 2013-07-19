package brooklyn.extras.openshift;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.webapp.ElasticJavaWebAppService;
import brooklyn.entity.webapp.ElasticJavaWebAppService.ElasticJavaWebAppServiceAwareLocation;
import brooklyn.location.AddressableLocation;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

class OpenshiftLocation extends AbstractLocation implements AddressableLocation, ElasticJavaWebAppServiceAwareLocation {

    public OpenshiftLocation() {
        super(MutableMap.of());
    }
    
    public OpenshiftLocation(Map properties) {
        super(properties);
    }

    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("hostname", getHostname()).add("url", getUrl()).add("user", getUser())
                .toString();
    }


    public String getHostname() {
        return (String) elvis(getConfigBag().getStringKey("hostname"), "openshift.redhat.com");
    }
    
    public String getUrl() {
        return (String) elvis(getConfigBag().getStringKey("url"), "https://"+getHostname()+"/broker");
    }
    
    public String getUser() {
        String result = elvis(getConfigBag().getStringKey("user"), getConfigBag().getStringKey("username"), null);
        return Preconditions.checkNotNull(result);
    }

    public String getUsername() {
        return getUser();
    }
        
    public String getPassword() {
        String result = (String) elvis(getConfigBag().getStringKey("password"), null);
        return Preconditions.checkNotNull(result);
    }
    
    public static class Resolver implements LocationResolver {
        
        private ManagementContext managementContext;
    
        @Override
        public void init(ManagementContext managementContext) {
            this.managementContext = checkNotNull(managementContext, "managementContext");
        }
        
        @Override
        public String getPrefix() {
            return "openshift";
        }

        @Override
        public Location newLocationFromString(Map properties, String spec) {
            return newLocationFromString(spec, null, properties, new MutableMap());
        }
        
        @Override
        public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
            return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
        }

        protected Location newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
            assert spec.equals(getPrefix()) : "location '"+getPrefix()+"' is not currently parametrisable (invalid '"+spec+"')";
            //TODO could support multiple URL/endpoints?
            return new OpenshiftLocation(properties);
        }

        @Override
        public boolean accepts(String spec, LocationRegistry registry) {
            // TODO Auto-generated method stub
            return false;
        }
    }

    @Override
    public ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory() {
        throw new UnsupportedOperationException("ElasticJavaWebAppService is work in progress");
        //TODO
//        return new OpenShiftExpressJavaWebAppCluster.Factory();
    }

    @Override
    public InetAddress getAddress() {
        try {
            return InetAddress.getByName(getHostname());
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
    }

}
