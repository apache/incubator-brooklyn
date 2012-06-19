package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class LocationSpec {

  public static LocationSpec localhost() {
    return new LocationSpec("localhost", null);
  }

  private final String provider;
  private final Map<String, String> config;


  public LocationSpec(
    @JsonProperty("provider") String provider,
    @JsonProperty("config") @Nullable Map<String, String> config
  ) {
    this.provider = checkNotNull(provider, "provider");
    this.config = (config == null) ? Collections.<String, String>emptyMap() : ImmutableMap.copyOf(config);
  }

  public String getProvider() {
    return provider;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocationSpec that = (LocationSpec) o;

    if (config != null ? !config.equals(that.config) : that.config != null)
      return false;
    if (provider != null ? !provider.equals(that.provider) : that.provider != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = provider != null ? provider.hashCode() : 0;
    result = 31 * result + (config != null ? config.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "LocationSpec{" +
      "provider='" + provider + '\'' +
      ", config=" + config +
      '}';
  }
}
