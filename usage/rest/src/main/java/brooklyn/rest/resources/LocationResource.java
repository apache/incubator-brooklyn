package brooklyn.rest.resources;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.util.EntityLocationUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/locations")
@Apidoc("Locations")
@Produces(MediaType.APPLICATION_JSON)
public class LocationResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Fetch the list of locations",
      responseClass = "brooklyn.rest.domain.LocationSummary",
      multiValueResponse = true)
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
      return LocationSummary.newInstance(l);
      
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
  @GET
  @Path("/usage/LocatedLocations")
  @ApiOperation(value = "Return a summary of all usage", notes="interim API, expected to change")
  public Map<String,Map<String,Object>> getLocatedLocations() {
      Map<String,Map<String,Object>> result = new LinkedHashMap<String,Map<String,Object>>();
      Map<Location, Integer> counts = new EntityLocationUtils(mgmt()).countLeafEntitiesByLocatedLocations();
      for (Map.Entry<Location,Integer> count: counts.entrySet()) {
          Location l = count.getKey();
          Map<String,Object> m = MutableMap.<String,Object>of(
                  "name", l.getName(),
                  "leafEntityCount", count.getValue(),
                  "latitude", l.getLocationProperty("latitude"),
                  "longitude", l.getLocationProperty("longitude")
              );
          result.put(l.getId(), m);
      }
      return result;
  }

  @GET
  @Path("/{locationId}")
  @ApiOperation(value = "Fetch details about a location",
      responseClass = "brooklyn.rest.domain.LocationSummary",
      multiValueResponse = true)
  public LocationSummary get(
          @ApiParam(value = "Location id to fetch", required = true)
          @PathParam("locationId") String locationId) {
      LocationDefinition l = brooklyn().getLocationRegistry().getDefinedLocation(locationId);
      if (l==null) throw WebResourceUtils.notFound("No location matching %s", locationId);
      return resolveLocationDefinition(l);
  }

  @POST
  @ApiOperation(value = "Create a new location", responseClass = "String")
  public Response create(
          @ApiParam(name = "locationSpec", value = "Location specification object", required = true)
          @Valid LocationSpec locationSpec) {
      String id = Identifiers.makeRandomId(8);
      LocationDefinition l = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
      brooklyn().getLocationRegistry().updateDefinedLocation(l);
      return Response.created(URI.create(id)).build();
  }

  @DELETE
  @Path("/{locationId}")
  @ApiOperation(value = "Delete a location object by id")
  public void delete(
      @ApiParam(value = "Location id to delete", required = true)
      @PathParam("locationId") String locationId) {
      brooklyn().getLocationRegistry().removeDefinedLocation(locationId);
  }

}
