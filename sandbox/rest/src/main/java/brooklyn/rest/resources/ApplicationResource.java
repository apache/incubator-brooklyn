package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
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
import javax.ws.rs.core.MediaType;

@Path("/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

  private final ApplicationManager manager;

  public ApplicationResource(ApplicationManager manager) {
    this.manager = manager;
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
    manager.registerAndStart(application);
  }

  @DELETE
  @Path("{application}")
  public void delete(@PathParam("application") String application) {
    manager.destroy(application);
  }
}
