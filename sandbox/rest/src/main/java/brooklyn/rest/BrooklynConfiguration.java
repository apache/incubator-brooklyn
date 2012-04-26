package brooklyn.rest;

import brooklyn.rest.api.Location;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.yammer.dropwizard.config.Configuration;
import java.util.Set;

public class BrooklynConfiguration extends Configuration {

  private boolean stopApplicationsOnExit = true;

  private Set<Location> locations = Sets.newHashSet();

  private ExecutorConfiguration executor = new ExecutorConfiguration();

  public Set<Location> getLocations() {
    return ImmutableSet.copyOf(locations);
  }

  public boolean isStopApplicationsOnExit() {
    return stopApplicationsOnExit;
  }

  public ExecutorConfiguration getExecutor() {
    return executor;
  }
}
