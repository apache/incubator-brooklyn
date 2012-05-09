package brooklyn.rest.core;

import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.LocationSpec;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationStore implements Managed {

  private final static Pattern refPattern = Pattern.compile("^/v1/locations/(\\d+)$");

  private final Map<Integer, LocationSpec> locations = Maps.newConcurrentMap();
  private final AtomicInteger ids = new AtomicInteger(0);

  public static LocationStore withLocalhost() {
    return new LocationStore(LocationSpec.localhost());
  }

  public LocationStore(LocationSpec... locationSpecs) {
    for (LocationSpec loc : locationSpecs) put(loc);
  }

  public LocationStore(BrooklynConfiguration configuration) {
    for (LocationSpec locationSpec : configuration.getLocations()) {
      put(locationSpec);
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

  public int put(LocationSpec locationSpec) {
    int id = ids.getAndIncrement();
    locations.put(id, locationSpec);
    return id;
  }

  public LocationSpec get(Integer id) {
    return locations.get(id);
  }

  public LocationSpec getByRef(String ref) {
    Matcher matcher = refPattern.matcher(ref);
    checkArgument(matcher.matches(), "URI '%s' does not match pattern '%s'", ref, refPattern);

    return get(Integer.parseInt(matcher.group(1)));
  }

  public Set<Map.Entry<Integer, LocationSpec>> entries() {
    return ImmutableSet.copyOf(locations.entrySet());
  }

  public boolean remove(int id) {
    return locations.remove(id) != null;
  }

}
