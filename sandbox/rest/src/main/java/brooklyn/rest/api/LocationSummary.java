package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class LocationSummary {

  private final static Set<String> SENSITIVE_CONFIGS =
      ImmutableSet.of("credential", "privateKeyFile", "sshPrivateKey", "rootSshPrivateKey", "password");

  private final String provider;
  private final Map<String, String> config;
  private final Map<String, URI> links;

  public LocationSummary(
      @JsonProperty("provider") String provider,
      @JsonProperty("config") Map<String, String> config,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.provider = checkNotNull(provider, "provider");
    this.config = (config == null) ? Collections.<String, String>emptyMap()
        : ImmutableMap.copyOf(config);
    this.links = ImmutableMap.copyOf(links);
  }

  public LocationSummary(String id, LocationSpec locationSpec) {
    this.provider = locationSpec.getProvider();
    this.config = copyConfigsExceptSensitiveKeys(locationSpec);
    this.links = ImmutableMap.of(
        "self", URI.create("/v1/locations/" + id)
    );
  }

  private Map<String, String> copyConfigsExceptSensitiveKeys(LocationSpec locationSpec) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> entry : locationSpec.getConfig().entrySet()) {
      if (!SENSITIVE_CONFIGS.contains(entry.getKey())) {
        builder.put(entry);
      }
    }
    return builder.build();
  }

  public String getProvider() {
    return provider;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocationSummary that = (LocationSummary) o;

    if (config != null ? !config.equals(that.config) : that.config != null)
      return false;
    if (links != null ? !links.equals(that.links) : that.links != null)
      return false;
    if (provider != null ? !provider.equals(that.provider) : that.provider != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = provider != null ? provider.hashCode() : 0;
    result = 31 * result + (config != null ? config.hashCode() : 0);
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "LocationSummary{" +
        "provider='" + provider + '\'' +
        ", config=" + config +
        ", links=" + links +
        '}';
  }
}
