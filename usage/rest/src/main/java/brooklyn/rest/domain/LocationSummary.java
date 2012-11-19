package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;

import com.google.common.collect.ImmutableMap;

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

  public static LocationSummary newInstance(String id, LocationSpec locationSpec) {
      return new LocationSummary(
              id,
              locationSpec.getName(), 
              locationSpec.getSpec(), 
              copyConfigsExceptSensitiveKeys(locationSpec.getConfig().entrySet()),
              ImmutableMap.of("self", URI.create("/v1/locations/" + id)) );
  }

  public static LocationSummary newInstance(LocationDefinition l) {
      return new LocationSummary(
              l.getId(),
              l.getName(), 
              l.getSpec(), 
              copyConfigsExceptSensitiveKeys(l.getConfig().entrySet()),
              ImmutableMap.of("self", URI.create("/v1/locations/" + l.getId())) );
  }

  /** creates an instance using the given location object to get add'l config entries */
  public static LocationSummary newInstance(LocationDefinition ld, Location ll) {
      return new LocationSummary(
              ld.getId(),
              ld.getName(), 
              ld.getSpec(), 
              copyConfigsExceptSensitiveKeys(ll.getLocationProperties().entrySet()),
              ImmutableMap.of("self", URI.create("/v1/locations/" + ld.getId())) );
  }

  private static Map<String, String> copyConfigsExceptSensitiveKeys(@SuppressWarnings("rawtypes") Set entries) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Object entryO : entries) {
      @SuppressWarnings("unchecked")
      Map.Entry<String, ?> entry = (Entry<String, ?>) entryO;
      if (!Entities.isSecret(entry.getKey())) {
          builder.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
      }
    }
    return builder.build();
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
    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (id != null ? id.hashCode() : 0);
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
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
