package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static com.google.common.base.Preconditions.checkNotNull;

@Path("/v1/applications/{application}/entities")
@Api(value = "/v1/applications/{application}/entities", description = "Manage entities")
@Produces("application/json")
public class EntityResource extends BaseResource {

  private final ApplicationManager manager;

  public EntityResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  @ApiOperation(value = "Fetch the list of entities for a given application",
      responseClass = "brooklyn.rest.api.EntitySummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application not found")
  })
  public Iterable<EntitySummary> list(
      @ApiParam(value = "The application name", required = true)
      @PathParam("application") final String applicationName) {
    Application application = getApplicationOr404(manager.registry(), applicationName);

    return summaryForChildrenEntities(application, application.getInstance());
  }

  @GET
  @Path("/{entity}")
  @ApiOperation(value = "Fetch details about a specific application entity",
      responseClass = "brooklyn.rest.api.EntitySummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public EntitySummary get(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(value = "Application entity", required = true)
      @PathParam("entity") String entityIdOrName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return new EntitySummary(application, entity);
  }

  @GET
  @Path("/{entity}/entities")
  public Iterable<EntitySummary> getChildren(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    Entity entity = getEntityOr404(application, entityIdOrName);

    return summaryForChildrenEntities(application, entity);
  }

  private Iterable<EntitySummary> summaryForChildrenEntities(final Application application, Entity rootEntity) {
    return Iterables.transform(rootEntity.getOwnedChildren(),
        new Function<Entity, EntitySummary>() {
          @Override
          public EntitySummary apply(Entity entity) {
            return new EntitySummary(application, entity);
          }
        });
  }
}
