package brooklyn.rest.resources;

import brooklyn.rest.core.ApplicationManager;
import javax.ws.rs.Path;

@Path("/applications/{application}/sensors")
public class SensorResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = manager;
  }
}
