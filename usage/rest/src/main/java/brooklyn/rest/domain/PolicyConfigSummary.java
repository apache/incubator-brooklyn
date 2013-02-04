package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.Policy;

import com.google.common.collect.ImmutableMap;

public class PolicyConfigSummary extends ConfigSummary {

  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public PolicyConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("reconfigurable") boolean reconfigurable,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, type, description, defaultValue, reconfigurable, links);
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  public PolicyConfigSummary(ApplicationSummary application, EntityLocal entity, Policy policy, ConfigKey<?> config) {
      this(entity, policy, config);
      if (!entity.getApplicationId().equals(application.getInstance().getId()))
          throw new IllegalStateException("Application "+application+" does not match app "+entity.getApplication()+" of "+entity);
  }
  
  public PolicyConfigSummary(EntityLocal entity, Policy policy, ConfigKey<?> config) {
    super(config);
    
    String applicationUri = "/v1/applications/" + entity.getApplicationId();
    String entityUri = applicationUri + "/entities/" + entity.getId();
    String policyUri = entityUri + "/policies/" + policy.getId();
    
    this.links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(policyUri + "/config/" + config.getName()))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .put("policy", URI.create(policyUri))
        .build();
  }
  
  @Override
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public String toString() {
    return "PolicyConfigSummary{" +
        "name='" + getName() + '\'' +
        ", type='" + getType() + '\'' +
        '}';
  }
}
