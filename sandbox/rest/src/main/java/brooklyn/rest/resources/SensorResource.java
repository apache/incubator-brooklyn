package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import brooklyn.rest.core.ApplicationManager;
import static com.google.common.base.Preconditions.checkNotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@Path("/applications/{application}/sensors")
public class SensorResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Iterable<String> listAllSensors(@PathParam("application") String application) {
    if (!manager.registry().containsKey(application))
      throw new WebApplicationException(Response.Status.NOT_FOUND);

    Application current = manager.registry().get(application);
    if (current.getStatus() != Application.Status.RUNNING)
      throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);

    return current.getInstance().getSensors().keySet();
  }

  @GET
  @Path("{sensor}")
  public String getSensor(
      @PathParam("application") String application,
      @PathParam("sensor") String sensor
  ) {
    return null;
  }
}
