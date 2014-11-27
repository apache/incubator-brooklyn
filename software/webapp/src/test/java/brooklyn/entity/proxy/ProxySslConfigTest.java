package brooklyn.entity.proxy;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.TypeCoercions;

@Test
public class ProxySslConfigTest {

    @Test
    public void testFromMap() {
        ProxySslConfig config = TypeCoercions.coerce(MutableMap.of(
            "certificateSourceUrl", "file://tmp/cert.txt", 
            "keySourceUrl", "file://tmp/key.txt", 
            "keyDestination", "dest.txt", 
            "targetIsSsl", true, 
            "reuseSessions", true), 
            ProxySslConfig.class);
        Assert.assertEquals(config.getCertificateSourceUrl(), "file://tmp/cert.txt");
        Assert.assertEquals(config.getKeySourceUrl(), "file://tmp/key.txt");
        Assert.assertEquals(config.getKeyDestination(), "dest.txt");
        Assert.assertEquals(config.getTargetIsSsl(), true);
        Assert.assertEquals(config.getReuseSessions(), true);
    }
    
    @Test
    public void testFromMapWithNullsAndDefaults() {
        ProxySslConfig config = TypeCoercions.coerce(MutableMap.of(
            "certificateSourceUrl", "file://tmp/cert.txt", 
            "keySourceUrl", null, 
            "targetIsSsl", "false"), 
            ProxySslConfig.class);
        Assert.assertEquals(config.getCertificateSourceUrl(), "file://tmp/cert.txt");
        Assert.assertEquals(config.getKeySourceUrl(), null);
        Assert.assertEquals(config.getKeyDestination(), null);
        Assert.assertEquals(config.getTargetIsSsl(), false);
        Assert.assertEquals(config.getReuseSessions(), false);
    }
    
}
