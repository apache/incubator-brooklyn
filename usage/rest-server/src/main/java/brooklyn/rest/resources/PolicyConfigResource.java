package brooklyn.rest.resources;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.Policy;
import brooklyn.rest.api.PolicyConfigApi;
import brooklyn.rest.domain.PolicyConfigSummary;
import brooklyn.rest.transform.PolicyTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.flags.TypeCoercions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class PolicyConfigResource extends AbstractBrooklynRestResource implements PolicyConfigApi {

  @Override
  public List<PolicyConfigSummary> list(
      final String application, final String entityToken, final String policyToken
  ) {
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Policy policy = brooklyn().getPolicy(entity, policyToken);

    List<PolicyConfigSummary> result = Lists.newArrayList();
    for (ConfigKey<?> key : policy.getPolicyType().getConfigKeys()) {
        result.add(PolicyTransformer.policyConfigSummary(entity, policy, key));
    }
    return result;
  }

  // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
  // (and in sensors class)
  @Override
  public Map<String, Object> batchConfigRead(String application, String entityToken, String policyToken) {
    // TODO: add test
    Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
    Map<ConfigKey<?>, Object> source = policy.getAllConfig();
    Map<String, Object> result = Maps.newLinkedHashMap();
    for (Map.Entry<ConfigKey<?>, Object> ek: source.entrySet()) {
        result.put(ek.getKey().getName(), getValueForDisplay(policy, ek.getValue()));
    }
    return result;
  }

  @Override
  public String get(String application, String entityToken, String policyToken, String configKeyName) {
    Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
    ConfigKey<?> ck = policy.getPolicyType().getConfigKey(configKeyName);
    if (ck == null) throw WebResourceUtils.notFound("Cannot find config key '%s' in policy '%s' of entity '%s'", configKeyName, policy, entityToken);

    return getValueForDisplay(policy, policy.getConfig(ck));
  }

  @Override
  public Response set(
           String application, String entityToken, String policyToken, String configKeyName, String value
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
