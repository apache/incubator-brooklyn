package brooklyn.location.basic.jclouds;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author Shane Witbeck
 * @since 1/9/13
 */
public class JcloudsLocationTest {


    @Test
    public void generateGroupId() {

        String user = System.getProperty("user.name");

        String vcloudGroupId = JcloudsLocation.generateGroupId("vcloud");
        assertTrue(vcloudGroupId.startsWith("br-"));

        String fooGroupId = JcloudsLocation.generateGroupId("foo");
        assertTrue(fooGroupId.startsWith("brooklyn-"+user));
    }
}
