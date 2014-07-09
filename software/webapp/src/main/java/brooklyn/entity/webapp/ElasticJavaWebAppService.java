package brooklyn.entity.webapp;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;

public interface ElasticJavaWebAppService extends JavaWebAppService, Startable {

    public interface ElasticJavaWebAppServiceAwareLocation {
        ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory();
    }

    public static class Factory extends AbstractConfigurableEntityFactory<ElasticJavaWebAppService>
    implements EntityFactoryForLocation<ElasticJavaWebAppService> {

        public ElasticJavaWebAppService newEntity2(Map flags, Entity parent) {
            return new ControlledDynamicWebAppClusterImpl(flags, parent);
        }

        public ConfigurableEntityFactory<ElasticJavaWebAppService> newFactoryForLocation(Location l) {
            if (l instanceof ElasticJavaWebAppServiceAwareLocation) {
                return ((ElasticJavaWebAppServiceAwareLocation)l).newWebClusterFactory().configure(config);
            }
            //optional, fail fast if location not supported
            if (!(l instanceof MachineProvisioningLocation))
                throw new UnsupportedOperationException("cannot create this entity in location "+l);
            return this;
        }
    }
    
}
