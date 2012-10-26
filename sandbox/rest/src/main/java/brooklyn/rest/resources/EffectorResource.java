package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EffectorUtils;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.EffectorSummary;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.util.Time;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import com.yammer.dropwizard.logging.Log;

@Path("/v1/applications/{application}/entities/{entity}/effectors")
@Api(value = "/v1/applications/{application}/entities/{entity}/effectors", description = "Manage effectors")
@Produces("application/json")
public class EffectorResource extends BaseResource {

  public static final Log LOG = Log.forClass(EffectorResource.class);

  private final ApplicationManager manager;

  public EffectorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  @ApiOperation(value = "Fetch the list of effectors",
      responseClass = "brooklyn.rest.api.EffectorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity not found")
  })
  public List<EffectorSummary> list(
      @ApiParam(name = "application", value = "Application name", required = true)
      @PathParam("application") final String applicationName,
      @ApiParam(name = "entity", value = "Entity name", required = true)
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return Lists.newArrayList(transform(
        entity.getEntityType().getEffectors(),
        new Function<Effector<?>, EffectorSummary>() {
          @Override
          public EffectorSummary apply(Effector<?> effector) {
            return new EffectorSummary(application, entity, effector);
          }
        }));
  }

  @POST
  @Path("/{effector}")
  @ApiOperation(value = "Trigger an effector, returning either the return value (status 200) or an activity task ID on timeout (status 202)")
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
      
      // TODO test timeout; and should it be header, form, or what?
      @ApiParam(name = "timeout", value = "Amount of time before the server should respond with the activity task ID and status 202, " +
      		"rather than with the result of the effector; assumes milliseconds if no unit specified. " +
      		"'never' (blocking) is the default; " +
      		"'0' means always return task activity ID; " +
      		"and e.g. '1000' or '1s' will return a result if available within one second otherwise status 202 and the activity task ID.", 
      		required = false, defaultValue = "never")
      @HeaderParam("timeout")
      String timeout,
      
      @ApiParam(name = "parameters", value = "Effector parameters as key value pairs", required = false)
      @Valid 
      Map<String, String> parameters
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    final Effector<?> effector = EffectorUtils.findEffectorMatching(entity.getEntityType().getEffectors(), effectorName, parameters);
    if (effector == null) {
      throw notFound("Entity '%s' has no effector with name '%s'", entityIdOrName, effectorName);
    }

    Task<?> t = entity.invoke(effector, parameters);
    
    try {
        Object result = null;
        if (timeout==null || timeout.isEmpty() || "never".equalsIgnoreCase(timeout)) {
            result = t.get();
        } else {
            long timeoutMillis = Time.parseTimeString(timeout);
            try {
                if (timeoutMillis==0) throw new TimeoutException();
                result = t.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // TODO does this put the result in the right format ?
                return Response.status(Response.Status.ACCEPTED).entity( t.getId() ).build();
            }
        }
        // TODO does this put the result in the right format ?
        return Response.status(Response.Status.ACCEPTED).entity( result ).build();
    } catch (Exception e) {
        throw Exceptions.propagate(e);
    }
  }
}
