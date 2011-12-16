package brooklyn.entity.dns.geoscaling

import static org.testng.AssertJUnit.*

import java.util.LinkedHashSet
import java.util.Set

import org.testng.annotations.Test

import brooklyn.entity.dns.HostGeoInfo
import brooklyn.util.ResourceUtils


/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
class GeoscalingScriptGeneratorTest {
    
    private final static Set<HostGeoInfo> HOSTS = new LinkedHashSet<HostGeoInfo>();
    static {
        HOSTS.add(new HostGeoInfo("1.2.3.100", "Server 1", 40.0, -80.0));
        HOSTS.add(new HostGeoInfo("1.2.3.101", "Server 2", 30.0, 20.0));
    }
    
    
    @Test
    public void testScriptGeneration() {
        Date generationTime = new Date(0);
        String generatedScript = GeoscalingScriptGenerator.generateScriptString(generationTime, HOSTS);
        assertTrue(generatedScript.contains("1.2.3"));
        String expectedScript = new ResourceUtils(this).getResourceAsString("brooklyn/entity/dns/geoscaling/expectedScript.php");
        assertEquals(expectedScript, generatedScript);
        //also make sure leading slash is allowed
        String expectedScript2 = new ResourceUtils(this).getResourceAsString("/brooklyn/entity/dns/geoscaling/expectedScript.php");
        assertEquals(expectedScript, generatedScript);
    }
    
}
