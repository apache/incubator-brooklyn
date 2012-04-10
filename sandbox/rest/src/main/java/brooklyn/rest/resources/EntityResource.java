package brooklyn.rest.resources;

import brooklyn.rest.api.Entity;
import com.google.common.collect.ImmutableSet;
import com.yammer.dropwizard.jersey.params.IntParam;
import java.util.Set;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/entities")
@Produces(MediaType.APPLICATION_JSON)
public class EntityResource {

  @GET
  public Set<Entity> listAvailableEntities(
      @QueryParam("offset") @DefaultValue("0") IntParam offset, @QueryParam("limit") @DefaultValue("20") IntParam limit) {
    return ImmutableSet.of();
  }

  @GET
  @Path("{entity}")
  public Entity getDetails(@PathParam("entity") String entity) {
    if (entity.startsWith("brooklyn")) {
      return new Entity(entity, "Something");
    }
    throw new WebApplicationException(Response.Status.NOT_FOUND);
  }

}
