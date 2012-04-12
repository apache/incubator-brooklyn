package brooklyn.rest.resources;

import brooklyn.rest.api.Sensor;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/applications/{application}/sensors")
public class SensorResource {

  @GET
  public Set<Sensor> listSensors(@PathParam("application") String application) {
    return ImmutableSet.of();
  }
}
