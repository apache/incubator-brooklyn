package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.entity.Entity;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntitySummary;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities")
@Apidoc("Application entities")
@Produces("application/json")
public class EntityResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Fetch the list of entities for a given application",
      responseClass = "brooklyn.rest.domain.EntitySummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application not found")
  })
  public List<EntitySummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application) {
    return summaryForChildrenEntities(brooklyn().getApplication(application));
  }

  @GET
  @Path("/{entity}")
  @ApiOperation(value = "Fetch details about a specific application entity",
      responseClass = "brooklyn.rest.domain.EntitySummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public EntitySummary get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entity
  ) {
    return EntitySummary.fromEntity(brooklyn().getEntity(application, entity));
  }

  // TODO rename as "/children" ?
  @GET
  @Path("/{entity}/entities")
  public Iterable<EntitySummary> getChildren(
      @PathParam("application") final String application,
      @PathParam("entity") final String entity
  ) {
    return summaryForChildrenEntities(brooklyn().getEntity(application, entity));
  }

  private List<EntitySummary> summaryForChildrenEntities(Entity rootEntity) {
    return Lists.newArrayList(transform(
        rootEntity.getChildren(),
        new Function<Entity, EntitySummary>() {
          @Override
          public EntitySummary apply(Entity entity) {
            return EntitySummary.fromEntity(entity);
          }
        }));
  }
}
