package brooklyn;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class BrooklynVersionTest {

  @Test
  public void testGetVersion() {
      assertEquals(BrooklynVersion.get(), BrooklynVersion.INSTANCE.getVersionFromStatic());
  }
    
  @Test
  public void testGetHardcodedClasspathVersion() {
    assertEquals(BrooklynVersion.INSTANCE.getVersionFromClasspath(), "0.0.0-SNAPSHOT");
  }
  
}
