package brooklyn.rest;

import brooklyn.rest.core.ApplicationStore;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.health.GeneralHealthCheck;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.LocationResource;
import brooklyn.rest.resources.SensorResource;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Environment;

public class BrooklynService extends Service<BrooklynConfiguration> {

  protected BrooklynService() {
    super("brooklyn-rest");
  }

  @Override
  protected void initialize(BrooklynConfiguration configuration, Environment environment)
      throws Exception {

    LocationStore locationStore = new LocationStore(configuration);
    environment.manage(locationStore);
    environment.addResource(new LocationResource(locationStore));

    ApplicationStore applicationStore = new ApplicationStore(locationStore);
    environment.manage(applicationStore);
    environment.addResource(new ApplicationResource(applicationStore));

    environment.addResource(new EntityResource());
    environment.addResource(new SensorResource());
    environment.addResource(new EffectorResource());

    environment.addHealthCheck(new GeneralHealthCheck());
  }

  public static void main(String[] args) throws Exception {
    new BrooklynService().run(args);
  }
}
