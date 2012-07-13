package brooklyn.rest.resources;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EffectorUtils;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.EffectorSummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import com.yammer.dropwizard.logging.Log;

import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

@Path("/v1/applications/{application}/entities/{entity}/effectors")
@Api(value = "/v1/applications/{application}/entities/{entity}/effectors", description = "Manage effectors")
@Produces("application/json")
public class EffectorResource extends BaseResource {

  public static final Log LOG = Log.forClass(EffectorResource.class);

  private final ApplicationManager manager;
  private ExecutorService executorService;

  public EffectorResource(ApplicationManager manager, ExecutorService executorService) {
    this.manager = checkNotNull(manager, "manager");
    this.executorService = checkNotNull(executorService, "executorService");
  }

  @GET
  @ApiOperation(value = "Fetch the list of effectors",
      responseClass = "brooklyn.rest.api.EffectorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity not found")
  })
  public Iterable<EffectorSummary> list(
      @ApiParam(name = "application", value = "Application name", required = true)
      @PathParam("application") final String applicationName,
      @ApiParam(name = "entity", value = "Entity name", required = true)
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return transform(
        entity.getEntityType().getEffectors(),
        new Function<Effector<?>, EffectorSummary>() {
          @Override
          public EffectorSummary apply(Effector<?> effector) {
            return new EffectorSummary(application, entity, effector);
          }
        });
  }

  @POST
  @Path("/{effector}")
  @ApiOperation(value = "Trigger an effector")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or Entity not found or Entity has no effector with that name")
  })
  public Response trigger(
      @ApiParam(name = "application", value = "Name of the application", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(name = "entity", value = "Name of the entity", required = true)
      @PathParam("entity") String entityIdOrName,
      @ApiParam(name = "effector", value = "Name of the effector to trigger", required = true)
      @PathParam("effector") String effectorName,
      @ApiParam(name = "parameters", value = "Effector parameters as key value pairs", required = false)
      @Valid final Map<String, String> parameters
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    final Effector<?> effector = EffectorUtils.findEffectorMatching(entity.getEntityType().getEffectors(), effectorName, parameters);
    if (effector == null) {
      throw notFound("Entity '%s' has no effector with name '%s'", entityIdOrName, effectorName);
    }

    executorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          entity.invoke(effector, parameters);
        } catch (Exception e) {
          LOG.error(e, "Failed while invoking effector");
          throw Throwables.propagate(e);
        }
      }
    });

    return Response.status(Response.Status.ACCEPTED).build();
  }
}
