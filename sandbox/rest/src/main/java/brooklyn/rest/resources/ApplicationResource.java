package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.Entity;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
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
    this.manager = manager;
    this.entities = entities;
  }

  @GET
  public Set<Application> listRegisteredApplications() {
    return ImmutableSet.of();
  }

  @GET
  @Path("{application}")
  public Map getApplication(@PathParam("application") String application) {
    return ImmutableMap.of();
  }

  @POST
  public void create(@Valid Application application) {

    /*
     * All requested entities should be available
     */
    for (Entity entity : application.getEntities()) {
      if (entities.contains(entity.getName())) {
        throw new WebApplicationException(Response.Status.NOT_FOUND);
      }
    }

    /*
     * TODO All locations should exists
     */

    manager.registerAndStart(application);
  }

  @DELETE
  @Path("{application}")
  public void delete(@PathParam("application") String application) {
    manager.destroy(application);
  }
}
