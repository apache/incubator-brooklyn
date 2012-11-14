package brooklyn.rest.legacy;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.rest.domain.LocationSpec;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class LocationStore {

  private final static Pattern refPattern = Pattern.compile("^/v1/locations/(\\d+)$");

  private final Map<Integer, LocationSpec> locations = Maps.newConcurrentMap();
  private final AtomicInteger ids = new AtomicInteger(0);

  public static LocationStore withLocalhost() {
    return new LocationStore(LocationSpec.localhost());
  }

  public LocationStore(LocationSpec... locationSpecs) {
    for (LocationSpec loc : locationSpecs) put(loc);
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
