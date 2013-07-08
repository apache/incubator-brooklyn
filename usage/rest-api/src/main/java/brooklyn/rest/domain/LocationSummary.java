package brooklyn.rest.domain;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class LocationSummary extends LocationSpec {

  private final String id;
  private final Map<String, URI> links;

  public LocationSummary(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("spec") String spec,
      @JsonProperty("config") Map<String, String> config,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, spec, config);
    this.id = checkNotNull(id);
    this.links = ImmutableMap.copyOf(links);
  }

  public String getId() {
    return id;
}
  
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    LocationSummary that = (LocationSummary) o;
    return Objects.equal(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, links);
  }

  @Override
  public String toString() {
    return "LocationSummary{" +
        "id='" + getId() + '\'' +
        "name='" + getName() + '\'' +
        "spec='" + getSpec() + '\'' +
        ", config=" + getConfig() +
        ", links=" + links +
        '}';
  }

}
