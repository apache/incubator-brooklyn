package brooklyn.rest.core;

import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.Location;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LocationStore implements Managed {

  private final Map<Integer, Location> locations = Maps.newConcurrentMap();
  private final AtomicInteger ids = new AtomicInteger(0);

  public LocationStore(BrooklynConfiguration configuration) {
    for (Location location : configuration.getLocations()) {
      put(location);
    }
  }

  @Override
  public synchronized void start() throws Exception {
    // TODO: load credentials from external storage
  }

  @Override
  public synchronized void stop() throws Exception {
    // TODO: save credentials to external storage
  }

  public void put(Location location) {
    locations.put(ids.getAndIncrement(), location);
  }

  public Set<Map.Entry<Integer, Location>> entries() {
    return ImmutableSet.copyOf(locations.entrySet());
  }

  public void remove(int id) {
    locations.remove(id);
  }

}
