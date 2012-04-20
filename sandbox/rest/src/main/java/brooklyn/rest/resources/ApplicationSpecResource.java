package brooklyn.rest.resources;

import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationSpecResource {

  private final ApplicationManager manager;
  private final EntityResource entities;

  public ApplicationSpecResource(ApplicationManager manager, EntityResource entities) {
    this.manager = manager;
    this.entities = entities;
  }

  @GET
  public Iterable<ApplicationSpec> listRegisteredApplications() {
    return manager.entries();
  }

  @GET
  @Path("{application}")
  public Map getApplication(@PathParam("application") String application) {
    return ImmutableMap.of(
        "spec", manager.getSpec(application),
        "context", manager.getSpec(application).getDeployedContext()
    );
  }

  @POST
  public Response create(@Valid ApplicationSpec applicationSpec) {

    /*
     * All requested entities should be available
     */
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (entities.contains(entitySpec.getName())) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
    }

    /*
     * TODO All locations should exists
     */

    // TODO: make the deployed context start call in background
    manager.createInstanceAndStart(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return Response.created(ref).build();
  }

  @DELETE
  @Path("{application}")
  public Response delete(@PathParam("application") String application) {
    manager.destroy(application);
    return Response.status(Response.Status.ACCEPTED).build();
  }
}
