package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import static com.google.common.base.Preconditions.checkNotNull;
import java.net.URI;
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
public class ApplicationResource {

  private final ApplicationManager manager;
  private final EntityResource entities;

  public ApplicationResource(ApplicationManager manager, EntityResource entities) {
    this.manager = checkNotNull(manager, "manager");
    this.entities = checkNotNull(entities, "entities");
  }

  @GET
  public Iterable<Application> listRegisteredApplications() {
    return manager.registry().values();
  }

  @GET
  @Path("{application}")
  public Application getApplication(@PathParam("application") String name) {
    if (manager.registry().containsKey(name)) {
      return manager.registry().get(name);
    }
    throw new WebApplicationException(Response.Status.NOT_FOUND);
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
    manager.startInBackground(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return Response.created(ref).build();
  }

  @DELETE
  @Path("{application}")
  public Response delete(@PathParam("application") String application) {
    manager.destroyInBackground(application);
    return Response.status(Response.Status.ACCEPTED).build();
  }
}
