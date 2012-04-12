package brooklyn.rest.resources;

import brooklyn.rest.api.Sensor;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/applications/{application}/sensors")
public class SensorResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = manager;
  }

  @GET
  public Set<Sensor> listSensors(@PathParam("application") String application) {
    return ImmutableSet.of();
  }

  @GET
  @Path("{sensor}")
  public String getSensor(
      @PathParam("application") String application,
      @PathParam("sensor") String sensor
  ) {
    return "";
  }
}
