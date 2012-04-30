package brooklyn.rest.resources;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

@Path("/v1/applications/{application}/entities/{entity}/effectors")
public class EffectorResource extends BaseResource {

  private final ApplicationManager manager;
  private ExecutorService executorService;

  public EffectorResource(ApplicationManager manager, ExecutorService executorService) {
    this.manager = checkNotNull(manager, "manager");
    this.executorService = checkNotNull(executorService, "executorService");
  }

  @GET
  public Iterable<URI> list(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return transform(
        entity.getEffectors().entrySet(),
        new Function<Map.Entry<String, Effector<?>>, URI>() {
          @Override
          public URI apply(Map.Entry<String, Effector<?>> entry) {
            return URI.create(String.format("/v1/applications/%s/entities/%s/effectors/%s",
                applicationName, entityIdOrName, entry.getValue().getName()));
          }
        });
  }

  @POST
  @Path("{effector}")
  public Response trigger(
      @PathParam("application") String applicationName,
      @PathParam("entity") String entityIdOrName,
      @PathParam("effector") String effectorName,
      @Valid final Map<String, String> parameters
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    final Effector<?> effector = entity.getEffectors().get(effectorName);
    executorService.submit(new Runnable() {
      @Override
      public void run() {
        entity.invoke(effector, parameters);
      }
    });

    return Response.status(Response.Status.ACCEPTED).build();
  }
}
