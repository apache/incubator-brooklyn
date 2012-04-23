package brooklyn.rest.resources;

import brooklyn.entity.Effector;
import brooklyn.rest.core.ApplicationManager;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/applications/{application}/effectors")
public class EffectorResource {

  private final ApplicationManager manager;

  public EffectorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Set<Effector> listEffectors(@PathParam("application") String id) {
    return ImmutableSet.of();
  }

  @POST
  @Path("{effector}")
  public void triggerEffector(
      @PathParam("application") String application,
      @PathParam("effector") String effector) {

  }
}
