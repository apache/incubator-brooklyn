package brooklyn.rest;

import brooklyn.rest.api.LocationSpec;
import com.google.common.collect.Lists;
import com.yammer.dropwizard.config.Configuration;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Values for the fields in this Configuration object are loaded from a YAML/JSON config file.
 */
public class BrooklynConfiguration extends Configuration {

  @Valid
  @NotNull
  private boolean stopApplicationsOnExit = true;

  @Valid
  @NotNull
  @JsonProperty
  private List<LocationSpec> locations = Lists.newLinkedList();

  @Valid
  @NotNull
  @JsonProperty
  private ExecutorConfiguration executor = new ExecutorConfiguration();

  public List<LocationSpec> getLocations() {
    return locations;
  }

  public boolean isStopApplicationsOnExit() {
    return stopApplicationsOnExit;
  }

  public ExecutorConfiguration getExecutorConfiguration() {
    return executor;
  }
}
