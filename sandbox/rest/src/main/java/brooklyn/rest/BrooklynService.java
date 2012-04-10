package brooklyn.rest;

import brooklyn.rest.health.GeneralHealthCheck;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
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
    environment.addResource(new EntityResource());
    environment.addResource(new ApplicationResource());
    environment.addResource(new SensorResource());
    environment.addResource(new EffectorResource());
    environment.addHealthCheck(new GeneralHealthCheck());
  }

  public static void main(String[] args) throws Exception {
    new BrooklynService().run(args);
  }
}
