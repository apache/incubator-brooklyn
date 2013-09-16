package brooklyn.rest.resources;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.rest.api.LocationApi;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.transform.LocationTransformer;
import brooklyn.rest.util.EntityLocationUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class LocationResource extends AbstractBrooklynRestResource implements LocationApi {

    @Override
  public List<LocationSummary> list() {
    return Lists.newArrayList(Iterables.transform(brooklyn().getLocationRegistry().getDefinedLocations().values(),
        new Function<LocationDefinition, LocationSummary>() {
          @Override
          public LocationSummary apply(LocationDefinition l) {
            return resolveLocationDefinition(l);
          }
        }));
  }

  protected LocationSummary resolveLocationDefinition(LocationDefinition l) {
      return LocationTransformer.newInstance(l);
      
//      // full config could be nice -- except it's way too much (all of brooklyn.properties and system properties!)
//      // also, handle non-resolveable errors somewhat gracefully
//      try {
//          Location ll = mgmt().getLocationRegistry().resolve(l);
//          return LocationSummary.newInstance(l, ll);
//      } catch (Exception e) {
//          LocationSummary s1 = LocationSummary.newInstance(l);
//          return new LocationSummary(s1.getId(), s1.getName(), s1.getSpec(), 
//                  new MutableMap<String,String>(s1.getConfig()).add("WARNING", "Location invalid: "+e),
//                  s1.getLinks());
//      }
  }

  // this is here to support the web GUI's circles
    @Override
  public Map<String,Map<String,Object>> getLocatedLocations() {
      Map<String,Map<String,Object>> result = new LinkedHashMap<String,Map<String,Object>>();
      Map<Location, Integer> counts = new EntityLocationUtils(mgmt()).countLeafEntitiesByLocatedLocations();
      for (Map.Entry<Location,Integer> count: counts.entrySet()) {
          Location l = count.getKey();
          Map<String,Object> m = MutableMap.<String,Object>of(
                  "name", l.getDisplayName(),
                  "leafEntityCount", count.getValue(),
                  "latitude", l.getConfig(LocationConfigKeys.LATITUDE),
                  "longitude", l.getConfig(LocationConfigKeys.LONGITUDE)
              );
          result.put(l.getId(), m);
      }
      return result;
  }

   @Override
  public LocationSummary get(String locationId) {
      LocationDefinition l = brooklyn().getLocationRegistry().getDefinedLocationById(locationId);
      if (l==null) throw WebResourceUtils.notFound("No location matching %s", locationId);
      return resolveLocationDefinition(l);
  }

    @Override
  public Response create(LocationSpec locationSpec) {
      String id = Identifiers.makeRandomId(8);
      LocationDefinition l = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
      brooklyn().getLocationRegistry().updateDefinedLocation(l);
      return Response.created(URI.create(id)).build();
  }

  public void delete(String locationId) {
      brooklyn().getLocationRegistry().removeDefinedLocation(locationId);
  }

}
