package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.api.LocationSpec;
import brooklyn.rest.api.LocationSummary;
import brooklyn.rest.core.LocationStore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class LocationResourceTest extends BaseResourceTest {

  private LocationStore store;

  @Override
  protected void setUpResources() throws Exception {
    this.store = LocationStore.withLocalhost();
    addResource(new LocationResource(store));
  }

  @Test
  public void testListAllLocations() {
    Set<LocationSummary> locations = client().resource("/v1/locations")
        .get(new GenericType<Set<LocationSummary>>() {
        });
    LocationSummary location = Iterables.get(locations, 0);
    assertThat(location.getProvider(), is("localhost"));
    assertThat(location.getLinks().get("self"), is(URI.create("/v1/locations/0")));
  }

  @Test
  public void testGetASpecificLocation() {
    LocationSummary location = client().resource("/v1/locations/0").get(LocationSummary.class);
    assertThat(location.getProvider(), is("localhost"));
  }

  @Test
  public void testAddNewLocation() {
    Map<String, String> expectedConfig = ImmutableMap.of(
        "identity", "identity",
        "credential", "credential",
        "location", "us-east-1");
    ClientResponse response = client().resource("/v1/locations")
        .post(ClientResponse.class, new LocationSpec("aws-ec2", expectedConfig));

    LocationSummary location = client().resource(response.getLocation()).get(LocationSummary.class);
    assertThat(location.getProvider(), is("aws-ec2"));

    assertThat(location.getConfig().get("identity"), is("identity"));
    assertFalse(location.getConfig().containsKey("credential"));
  }

  @Test(dependsOnMethods = {"testAddNewLocation"})
  public void testDeleteLocation() {
    assertThat(store.entries().size(), is(2));

    ClientResponse response = client().resource("/v1/locations/1").delete(ClientResponse.class);
    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
    assertThat(store.entries().size(), is(1));
  }
}
