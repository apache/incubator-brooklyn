package brooklyn.rest.resources;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.Policy;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.PolicyConfigSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/policies/{policy}/config")
@Apidoc("Policy config")
@Produces("application/json")
public class PolicyConfigResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Fetch the config keys for a specific policy",
      responseClass = "brooklyn.rest.domain.ConfigSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity or policy")
  })
  public List<PolicyConfigSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken,
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") final String policyToken
  ) {
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Policy policy = brooklyn().getPolicy(entity, policyToken);

    List<PolicyConfigSummary> result = Lists.newArrayList();
    for (ConfigKey<?> key : policy.getPolicyType().getConfigKeys()) {
        result.add(new PolicyConfigSummary(entity, policy, key));
    }
    return result;
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
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") String policyToken) {
    // TODO: add test
    Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
    Map<ConfigKey<?>, Object> source = policy.getAllConfig();
    Map<String, Object> result = Maps.newLinkedHashMap();
    for (Map.Entry<ConfigKey<?>, Object> ek: source.entrySet()) {
        result.put(ek.getKey().getName(), getValueForDisplay(policy, ek.getValue()));
    }
    return result;
  }

  @GET
  @Path("/{config}")
  @ApiOperation(value = "Fetch config value", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity, policy or config key")
  })
  public String get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") String policyToken,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  ) {
    Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
    ConfigKey<?> ck = policy.getPolicyType().getConfigKey(configKeyName);
    if (ck == null) throw WebResourceUtils.notFound("Cannot find config key '%s' in policy '%s' of entity '%s'", configKeyName, policy, entityToken);

    return getValueForDisplay(policy, policy.getConfig(ck));
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @POST
  @Path("/{config}/set")
  @ApiOperation(value = "Sets the given config on this policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity, policy or config key")
  })
  public Response set(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          @ApiParam(value = "Policy ID or name", required = true)
          @PathParam("policy") String policyToken,
          @ApiParam(value = "Config key ID", required = true)
          @PathParam("config") String configKeyName,
          @ApiParam(name = "value", value = "New value for the configuration", required = true)
          @QueryParam("value") String value
  ) {
      Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
      ConfigKey<?> ck = policy.getPolicyType().getConfigKey(configKeyName);
      if (ck == null) throw WebResourceUtils.notFound("Cannot find config key '%s' in policy '%s' of entity '%s'", configKeyName, policy, entityToken);
      
      policy.setConfig((ConfigKey)ck, TypeCoercions.coerce(value, ck.getType()));
      
      return Response.status(Response.Status.OK).build();
  }

  // TODO Remove duplication from ConfigResource
  private String getValueForDisplay(Policy policy, Object value) {
    // currently everything converted to string, expanded if it is a "done" future
    if (value instanceof Future) {
        if (((Future<?>)value).isDone()) {
            try {
                value = ((Future<?>)value).get();
            } catch (Exception e) {
                value = ""+value+" (error evaluating: "+e+")";
            }
        }
    }
    return (value != null) ? value.toString() : null;
  }
}
