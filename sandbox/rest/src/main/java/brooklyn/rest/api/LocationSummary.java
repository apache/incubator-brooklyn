package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonProperty;

public class LocationSummary {

  private final String provider;
  private final String location;
  private final String identity;
  private final Map<String, URI> links;

  public LocationSummary(
      @JsonProperty("provider") String provider,
      @JsonProperty("location") String location,
      @JsonProperty("identity") String identity,
      @JsonProperty("links") Map<String, URI> links) {
    this.provider = checkNotNull(provider, "provider");
    this.location = location;
    this.identity = identity;
    this.links = ImmutableMap.copyOf(links);
  }

  public LocationSummary(String id, Location location) {
    this.provider = location.getProvider();
    this.location = location.getLocation();
    this.identity = location.getIdentity();
    this.links = ImmutableMap.of(
        "self", URI.create("/v1/locations/" + id)
    );
  }

  public String getProvider() {
    return provider;
  }

  public String getLocation() {
    return location;
  }

  public String getIdentity() {
    return identity;
  }

  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocationSummary that = (LocationSummary) o;

    if (identity != null ? !identity.equals(that.identity) : that.identity != null)
      return false;
    if (links != null ? !links.equals(that.links) : that.links != null)
      return false;
    if (location != null ? !location.equals(that.location) : that.location != null)
      return false;
    if (provider != null ? !provider.equals(that.provider) : that.provider != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = provider != null ? provider.hashCode() : 0;
    result = 31 * result + (location != null ? location.hashCode() : 0);
    result = 31 * result + (identity != null ? identity.hashCode() : 0);
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "LocationSummary{" +
        "provider='" + provider + '\'' +
        ", location='" + location + '\'' +
        ", identity='" + identity + '\'' +
        ", links=" + links +
        '}';
  }
}
