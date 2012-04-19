package brooklyn.rest.core;

import brooklyn.entity.nosql.redis.RedisStore;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ApplicationManagerTest {

  private LocationStore locationStore = LocationStore.withLocalhost();
  private ApplicationManager manager;


  @BeforeMethod
  public void setUp() {
    manager = new ApplicationManager(locationStore);
  }

  private ApplicationSpec createApplicationWithEntity(EntitySpec entitySpec) {
    return new ApplicationSpec("test-app", ImmutableSet.of(entitySpec),
        ImmutableSet.of("/locations/0"));
  }

  @Test
  public void testRegisterAndStartOnLocalhostWithArguments() {
    ApplicationSpec redis = createApplicationWithEntity(
        new EntitySpec("redis", "brooklyn.entity.nosql.redis.RedisStore",
            ImmutableMap.of("redisPort", "61234")));
    manager.createInstanceAndStart(redis);

    assertTrue(redis.isDeployed());

    RedisStore entity = (RedisStore) redis.getDeployedContext().getOwnedChildren().iterator().next();
    int port = entity.getAttribute(RedisStore.REDIS_PORT).intValue();
    assertEquals(port, 61234);
  }

  @AfterMethod
  public void tearDown() {
    manager.destroyAll();
  }

}
