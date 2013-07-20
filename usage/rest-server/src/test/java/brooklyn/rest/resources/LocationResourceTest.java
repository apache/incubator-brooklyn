package brooklyn.rest.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertFalse;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.test.Asserts;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class LocationResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(LocationResourceTest.class);
    private URI addedLocationUri;
    
  @Override
  protected void setUpResources() throws Exception {
    addResources();
  }

  @Test
  public void testAddNewLocation() {
    Map<String, String> expectedConfig = ImmutableMap.of(
        "identity", "bob",
        "credential", "CR3dential",
        "location", "us-east-1");
    ClientResponse response = client().resource("/v1/locations")
        .post(ClientResponse.class, new LocationSpec("my-jungle", "aws-ec2", expectedConfig));

    addedLocationUri = response.getLocation();
    log.info("added, at: "+addedLocationUri);
    LocationSummary location = client().resource(response.getLocation()).get(LocationSummary.class);
    log.info(" contents: "+location);
    assertThat(location.getSpec(), is("aws-ec2"));

    assertThat(location.getConfig().get("identity"), is("bob"));
    assertFalse(location.getConfig().containsKey("CR3dential"));
    Assert.assertTrue(addedLocationUri.toString().startsWith("/v1/locations/"));
    
    JcloudsLocation l = (JcloudsLocation) getManagementContext().getLocationRegistry().resolve(location.getId());
    Assert.assertEquals(l.getProvider(), "aws-ec2");
  }

  @Test(dependsOnMethods={"testAddNewLocation"})
  public void testListAllLocations() {
    Set<LocationSummary> locations = client().resource("/v1/locations")
        .get(new GenericType<Set<LocationSummary>>() {
        });
    Iterable<LocationSummary> matching = Iterables.filter(locations, new Predicate<LocationSummary>() {
        @Override
        public boolean apply(@Nullable LocationSummary l) {
            return "my-jungle".equals(l.getName());
        }
    });
    LocationSummary location = Iterables.getOnlyElement(matching);
    assertThat(location.getSpec(), is("aws-ec2"));
    Assert.assertEquals(location.getLinks().get("self"), addedLocationUri);
  }

  @Test(dependsOnMethods={"testListAllLocations"})
  public void testGetASpecificLocation() {
    LocationSummary location = client().resource(addedLocationUri.toString()).get(LocationSummary.class);
    assertThat(location.getSpec(), is("aws-ec2"));
  }

  @Test(dependsOnMethods = {"testGetASpecificLocation"})
  public void testDeleteLocation() {
    final int size = getLocationRegistry().getDefinedLocations().size();

    ClientResponse response = client().resource(addedLocationUri).delete(ClientResponse.class);
    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
      Asserts.succeedsEventually(new Runnable() {
        @Override
        public void run() {
            assertThat(getLocationRegistry().getDefinedLocations().size(), is(size - 1));
        }
    });
  }
}
