package brooklyn.rest.core;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.Entity;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ApplicationManagerTest {

  private LocationStore locationStore = LocationStore.withLocalhost();
  private ApplicationManager manager;

  private Entity redisEntity = new Entity("brooklyn.entity.nosql.redis.RedisStore",
      ImmutableMap.<String, String>of());

  @BeforeMethod
  public void setUp() {
    manager = new ApplicationManager(locationStore);
  }

  private Application createApplicationWithEntity(Entity entity) {
    return new Application("test-app", ImmutableSet.of(entity),
        ImmutableSet.of("/locations/0"));
  }

  @Test(enabled = false)
  public void testRegisterAndStartOnLocalhost() {
    Application redis = createApplicationWithEntity(redisEntity);
    manager.registerAndStart(redis);
  }

  @AfterMethod
  public void tearDown() {
    manager.destroyAll();
  }

}
