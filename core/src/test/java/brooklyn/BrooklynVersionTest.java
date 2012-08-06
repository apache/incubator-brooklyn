package brooklyn;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class BrooklynVersionTest {

  @Test
  public void testGetHardcodedVersion() {
    assertEquals(BrooklynVersion.get(), "0.0.0-SNAPSHOT");
  }
}
