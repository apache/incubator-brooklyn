package brooklyn.rest.core;

import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.Location;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import com.yammer.dropwizard.logging.Log;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationStore implements Managed {

  private final static Pattern refPattern = Pattern.compile("^/locations/(\\d+)$");

  private final Map<Integer, Location> locations = Maps.newConcurrentMap();
  private final AtomicInteger ids = new AtomicInteger(0);

  public static LocationStore withLocalhost() {
    return new LocationStore(Location.localhost());
  }

  public LocationStore(Location... locations) {
    for (Location loc : locations) put(loc);
  }

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

  public int put(Location location) {
    int id = ids.getAndIncrement();
    locations.put(id, location);
    return id;
  }

  public Location get(Integer id) {
    return locations.get(id);
  }

  public Location getByRef(String ref) {
    Matcher matcher = refPattern.matcher(ref);
    checkArgument(matcher.matches());

    return get(Integer.parseInt(matcher.group(1)));
  }

  public Set<Map.Entry<Integer, Location>> entries() {
    return ImmutableSet.copyOf(locations.entrySet());
  }

  public void remove(int id) {
    locations.remove(id);
  }

}
