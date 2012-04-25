package brooklyn.rest.core;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LocationStoreTest {

  LocationStore store;

  @BeforeMethod
  public void setUp() {
    store = LocationStore.withLocalhost();
  }

  @Test
  public void testGetLocationByRef() {
    assertEquals(store.get(0).getProvider(), "localhost");
    assertEquals(store.getByRef("/v1/locations/0").getProvider(), "localhost");
  }
}
