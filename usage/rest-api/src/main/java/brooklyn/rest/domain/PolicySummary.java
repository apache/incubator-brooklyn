package brooklyn.rest.domain;

import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Map;

public class PolicySummary {

  private final String id;
  private final String name;
  private final Status state;
  private final Map<String, URI> links;

  public PolicySummary(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("state") Status state,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.id = id;
    this.name = name;
    this.state = state;
    this.links = ImmutableMap.copyOf(links);
  }

  public String getId() {
      return id;
  }
  
  public String getName() {
    return name;
  }

  public Status getState() {
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
