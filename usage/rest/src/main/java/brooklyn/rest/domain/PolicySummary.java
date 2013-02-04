package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.Policies;

import com.google.common.collect.ImmutableMap;

public class PolicySummary {

  private final String id;
  private final String name;
  private final Lifecycle state;
  private final Map<String, URI> links;

  public PolicySummary(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("state") Lifecycle state,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.id = id;
    this.name = name;
    this.state = state;
    this.links = ImmutableMap.copyOf(links);
  }

  @Deprecated
  public PolicySummary(ApplicationSummary application, EntityLocal entity, Policy policy) {
      this(entity, policy);
      assert application.getId().equals(entity.getApplicationId());
  }
  protected PolicySummary(Entity entity, Policy policy) {
    this.id = policy.getId();
    this.name = policy.getName();
    this.state = Policies.getPolicyStatus(policy);
    
    String applicationUri = "/v1/applications/" + entity.getApplicationId();
    String entityUri = applicationUri + "/entities/" + entity.getId();

    this.links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(entityUri + "/policies/" + policy.getId()))
        .put("config", URI.create(entityUri + "/policies/" + policy.getId() + "/config"))
        .put("start", URI.create(entityUri + "/policies/" + policy.getId() + "/start"))
        .put("stop", URI.create(entityUri + "/policies/" + policy.getId() + "/stop"))
        .put("destroy", URI.create(entityUri + "/policies/" + policy.getId() + "/destroy"))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .build();
  }
  
  public static PolicySummary fromEntity(Entity entity, Policy policy) {
      return new PolicySummary(entity, policy);
  }

  public String getId() {
      return id;
  }
  
  public String getName() {
    return name;
  }

  public Lifecycle getState() {
    return state;
  }
  
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PolicySummary that = (PolicySummary) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
        return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
        return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (id != null ? id.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ConfigSummary{" +
        "name='" + name + '\'' +
        ", id='" + id + '\'' +
        ", links=" + links +
        '}';
  }

}
