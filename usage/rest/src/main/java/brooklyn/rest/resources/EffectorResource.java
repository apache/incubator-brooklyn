package brooklyn.rest.resources;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EffectorUtils;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.Time;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/effectors")
@Apidoc("Entity effectors")
@Produces("application/json")
public class EffectorResource extends AbstractBrooklynRestResource {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EffectorResource.class);
    
  @GET
  @ApiOperation(value = "Fetch the list of effectors",
      responseClass = "brooklyn.rest.domain.EffectorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<EffectorSummary> list(
      @ApiParam(name = "application", value = "Application name", required = true)
      @PathParam("application") final String application,
      @ApiParam(name = "entity", value = "Entity name", required = true)
      @PathParam("entity") final String entityToken
  ) {
      final EntityLocal entity = brooklyn().getEntity(application, entityToken);
      return Lists.newArrayList(transform(
        entity.getEntityType().getEffectors(),
        new Function<Effector<?>, EffectorSummary>() {
          @Override
          public EffectorSummary apply(Effector<?> effector) {
            return EffectorSummary.fromEntity(entity, effector);
          }
        }));
  }

  @POST
  @Path("/{effector}")
  @ApiOperation(value = "Trigger an effector",
    notes="Returns the return value (status 200) if it completes, or an activity task ID (status 202) if it times out")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or effector")
  })
  public Response trigger(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "effector", value = "Name of the effector to trigger", required = true)
      @PathParam("effector") String effectorName,
      
      // TODO test timeout; and should it be header, form, or what?
      @ApiParam(name = "timeout", value = "Delay before server should respond with activity task ID rather than result (in millis if no unit specified): " +
      		"'never' (blocking) is default; " +
      		"'0' means 'always' return task activity ID; " +
      		"and e.g. '1000' or '1s' will return a result if available within one second otherwise status 202 and the activity task ID", 
      		required = false, defaultValue = "never")
      @HeaderParam("timeout")
      String timeout,
      
      @ApiParam(name = "parameters", value = "Effector parameters (as key value pairs)", required = false)
      @Valid 
      Map<String, String> parameters
  ) {
      final EntityLocal entity = brooklyn().getEntity(application, entityToken);

    final Effector<?> effector = EffectorUtils.findEffectorMatching(entity.getEntityType().getEffectors(), effectorName, parameters);
    if (effector == null) {
      throw WebResourceUtils.notFound("Entity '%s' has no effector with name '%s'", entityToken, effectorName);
    }

    Task<?> t = entity.invoke(effector, parameters);
    
    try {
        Object result = null;
        if (timeout==null || timeout.isEmpty() || "never".equalsIgnoreCase(timeout)) {
            result = t.get();
        } else {
            long timeoutMillis = "always".equalsIgnoreCase(timeout) ? 0 : Time.parseTimeString(timeout);
            try {
                if (timeoutMillis==0) throw new TimeoutException();
                result = t.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return Response.status(Response.Status.ACCEPTED).entity( TaskSummary.fromTask(t) ).build();
            }
        }
        return Response.status(Response.Status.ACCEPTED).entity( result ).build();
    } catch (Exception e) {
        throw Exceptions.propagate(e);
    }
  }
  
}
