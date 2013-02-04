package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntityConfigSummary;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/config")
@Apidoc("Entity config")
@Produces("application/json")
public class EntityConfigResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Fetch the config keys for a specific application entity",
      responseClass = "brooklyn.rest.domain.ConfigSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<EntityConfigSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) {
    final EntityLocal entity = brooklyn().getEntity(application, entityToken);

    return Lists.newArrayList(transform(
        entity.getEntityType().getConfigKeys(),
        new Function<ConfigKey<?>, EntityConfigSummary>() {
          @Override
          public EntityConfigSummary apply(ConfigKey<?> config) {
            return new EntityConfigSummary(entity, config);
          }
        }));
  }

  // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
  // (and in sensors class)
  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch config key values in batch", notes="Returns a map of config name to value")
  public Map<String, Object> batchConfigRead(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken) {
    // TODO: add test
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Map<ConfigKey<?>, Object> source = ((EntityInternal)entity).getAllConfig();
    Map<String, Object> result = Maps.newLinkedHashMap();
    for (Map.Entry<ConfigKey<?>, Object> ek: source.entrySet()) {
        Object value = ek.getValue();
        // TODO support use raw types as parameter, here and everywhere
        result.put(ek.getKey().getName(), (value != null) ? value.toString() : null);
    }
    return result;
  }

  @GET
  @Path("/{config}")
  @ApiOperation(value = "Fetch config value", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or config key")
  })
  public String get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  ) {
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    ConfigKey<?> ck = entity.getEntityType().getConfigKey(configKeyName);
    if (ck==null) ck = new BasicConfigKey<Object>(Object.class, configKeyName);
    Object value = entity.getConfig(ck);
    return (value != null) ? value.toString() : null;
  }

}
