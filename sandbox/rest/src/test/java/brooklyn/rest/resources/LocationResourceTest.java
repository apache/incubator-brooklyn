package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.api.Location;
import brooklyn.rest.core.LocationStore;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import java.util.Set;
import javax.ws.rs.core.Response;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
    Set<Location> locations = client().resource("/v1/locations").get(new GenericType<Set<Location>>() {
    });
    Location location = Iterables.get(locations, 0);
    assertThat(location.getProvider(), is("localhost"));
    assertNull(location.getCredential());
  }

  @Test
  public void testGetASpecificLocation() {
    Location location = client().resource("/v1/locations/0").get(Location.class);
    assertThat(location.getProvider(), is("localhost"));
    assertNull(location.getCredential());
  }

  @Test
  public void testAddNewLocation() {
    ClientResponse response = client().resource("/v1/locations")
        .post(ClientResponse.class, new Location("aws-ec2", "identity", "credential", "us-east-1"));

    Location location = client().resource(response.getLocation()).get(Location.class);
    assertThat(location.getProvider(), is("aws-ec2"));
    assertNull(location.getCredential());
  }

  @Test(dependsOnMethods = {"testAddNewLocation"})
  public void testDeleteLocation() {
    assertThat(store.entries().size(), is(2));

    ClientResponse response = client().resource("/v1/locations/1").delete(ClientResponse.class);
    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
    assertThat(store.entries().size(), is(1));
  }
}
