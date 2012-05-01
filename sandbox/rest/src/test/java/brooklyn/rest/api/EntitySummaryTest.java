package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import java.net.URI;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class EntitySummaryTest {

  final EntitySummary entitySummary = new EntitySummary(
      "brooklyn.entity.nosql.redis.RedisStore",
      ImmutableMap.of("self", URI.create("/v1/applications/redis-app/entities/redis-ent")));

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(entitySummary), jsonFixture("fixtures/entity-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity-summary.json"), EntitySummary.class), entitySummary);
  }

}
