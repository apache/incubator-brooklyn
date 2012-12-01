package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class LocationSpec {

  private final String name;
  private final String spec;
  private final Map<String, String> config;

  public static LocationSpec localhost() {
    return new LocationSpec("localhost", "localhost", null);
  }

  public LocationSpec(
      @JsonProperty("name") String name,
      @JsonProperty("spec") String spec,
      @JsonProperty("config") @Nullable Map<String, String> config
  ) {
    this.name = name;
    this.spec = checkNotNull(spec, "spec");
    this.config = (config == null) ? Collections.<String, String>emptyMap() : ImmutableMap.copyOf(config);
  }

  public String getName() {
    return name;
}
  
  public String getSpec() {
    return spec;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocationSpec that = (LocationSpec) o;
    return Objects.equal(name, that.name) && Objects.equal(spec, that.spec) && Objects.equal(config, that.config);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(spec, name, config);
  }

  @Override
  public String toString() {
    return "LocationSpec{" +
        "name='" + name + '\'' +
        "spec='" + spec + '\'' +
        ", config=" + config +
        '}';
  }
}
