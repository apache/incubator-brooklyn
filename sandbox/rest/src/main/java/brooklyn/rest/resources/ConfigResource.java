package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ConfigSummary;
import brooklyn.rest.core.ApplicationManager;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/config")
@Api(value = "/v1/applications/{application}/entities/{entity}/config", description = "Manage configuration for each application entity")
@Produces("application/json")
public class ConfigResource extends BaseResource {

  private final ApplicationManager manager;

  public ConfigResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  @ApiOperation(value = "Fetch the config keys for a specific application entity",
      responseClass = "brooklyn.rest.api.SensorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<ConfigSummary> list(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") final String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager, applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return Lists.newArrayList(transform(filter(
        entity.getEntityType().getConfigKeys(),
        new Predicate<ConfigKey<?>>() {
          @Override
          public boolean apply(@Nullable ConfigKey<?> input) {
            return true;
          }
        }),
        new Function<ConfigKey<?>, ConfigSummary>() {
          @Override
          public ConfigSummary apply(ConfigKey<?> config) {
            return new ConfigSummary(application, entity, config);
          }
        }));
  }

  // TODO support parameters  ?show=value,summary&name=xxx
  // (and in sensors class)
  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch config key values in batch", notes="Returns a map of config name to value")
  public Map<String, Object> batchConfigRead(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") String entityId) {
    // TODO: add test
    Application application = getApplicationOr404(manager, applicationName);
    EntityLocal entity = getEntityOr404(application, entityId);
    Map<ConfigKey<?>, Object> source = ((AbstractEntity)entity).getConfigMap().getAllConfig();
    Map<String, Object> result = Maps.newLinkedHashMap();
    for (Map.Entry<ConfigKey<?>, Object> ek: source.entrySet()) {
        Object value = ek.getValue();
        // TODO use raw types, here and everywhere
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
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") String entityId,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  ) {
    Application application = getApplicationOr404(manager, applicationName);
    EntityLocal entity = getEntityOr404(application, entityId);

    // TODO find a relevant key
    ConfigKey<?> ck = entity.getEntityType().getConfigKey(configKeyName);
    if (ck==null) ck = new BasicConfigKey<Object>(Object.class, configKeyName);
    Object value = entity.getConfig(ck);
    return (value != null) ? value.toString() : null;
  }

}
