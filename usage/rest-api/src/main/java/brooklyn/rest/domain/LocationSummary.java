package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class LocationSummary extends LocationSpec {

  private final String id;
  
  /** only intended for instantiated Locations, not definitions */ 
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String type;
  private final Map<String, URI> links;

  public LocationSummary(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("spec") String spec,
      @JsonProperty("type") String type,
      @JsonProperty("config") Map<String, ?> config,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, spec, config);
    this.id = checkNotNull(id);
    this.type = type;
    this.links = ImmutableMap.copyOf(links);
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
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
        "type='" + getType() + '\'' +
        ", config=" + getConfig() +
        ", links=" + links +
        '}';
  }

}
