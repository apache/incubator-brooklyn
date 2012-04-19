package brooklyn.rest.core;

import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ApplicationManagerTest {

  private LocationStore locationStore = LocationStore.withLocalhost();
  private ApplicationManager manager;

  private EntitySpec redisEntitySpec = new EntitySpec("redis", "brooklyn.entity.nosql.redis.RedisStore");

  @BeforeMethod
  public void setUp() {
    manager = new ApplicationManager(locationStore);
  }

  private ApplicationSpec createApplicationWithEntity(EntitySpec entitySpec) {
    return new ApplicationSpec("test-app", ImmutableSet.of(entitySpec),
        ImmutableSet.of("/locations/0"));
  }

  @Test
  public void testRegisterAndStartOnLocalhost() {
    ApplicationSpec redis = createApplicationWithEntity(redisEntitySpec);
    manager.createInstanceAndStart(redis);
  }

  @AfterMethod
  public void tearDown() {
    manager.destroyAll();
  }

}
