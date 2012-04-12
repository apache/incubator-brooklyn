package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

  @GET
  public Set<Application> listRegisteredApplications() {
    return ImmutableSet.of();
  }

  @POST
  public void create(@Valid Application application) {
  }
}
