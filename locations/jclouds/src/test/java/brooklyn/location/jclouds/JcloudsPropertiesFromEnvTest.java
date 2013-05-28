package brooklyn.location.jclouds;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;

import brooklyn.util.collections.MutableMap;

public class JcloudsPropertiesFromEnvTest {
    protected static Map<String, Object> newSampleProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.jclouds.FooServers.identity", "bob");
        map.put("brooklyn.jclouds.FooServers.credential", "s3cr3t");
        return map;
    }

    protected static Map<String, String> newSamplePropsCli() {
        return MutableMap.of("JCLOUDS_IDENTITY_FOOSERVERS", "clive", "JCLOUDS_CREDENTIAL_FOOSERVERS", "s3cr3t");
    }

    protected static Map<String, Object> newSampleNamedProps() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("brooklyn.location.named.cloudfirst", "jclouds:openstack-nova");
        map.put("brooklyn.location.named.cloudfirst.identity", "myId");
        map.put("brooklyn.location.named.cloudfirst.credential", "password");
        map.put("brooklyn.location.named.cloudfirst.imageId", "RegionOne/89992f53-6ef8-4933-a08e-e7cba5601fe8");
        map.put("brooklyn.location.named.cloudfirst.image-id", "RegionOne/89992f53-6ef8-4933-a08e-e7cba5601fe8");
        map.put("brooklyn.location.named.cloudfirst.securityGroups", "universal");
        return map;
    }

    @Test
    public void testProviderSettings() {
        Map<String, Object> map = JcloudsPropertiesFromEnv.getJcloudsPropertiesFromEnv("FooServers", newSampleProps());
        Assert.assertEquals(map.get("identity"), "bob");
        Assert.assertEquals(map.get("credential"), "s3cr3t");
        Assert.assertEquals(map.get("provider"), "FooServers");
    }

    @Test
    public void testNamedPropertiesSettings() {
        Map<String, Object> map = JcloudsPropertiesFromEnv.getJcloudsPropertiesFromEnv("", "cloudfirst", newSampleNamedProps());
        Assert.assertEquals(map.get("provider"), "openstack-nova");
        Assert.assertEquals(map.get("identity"), "myId");
        Assert.assertEquals(map.get("credential"), "password");
        Assert.assertEquals(map.get("imageId"), "RegionOne/89992f53-6ef8-4933-a08e-e7cba5601fe8");
        Assert.assertEquals(map.get("securityGroups"), "universal");
        Assert.assertNull(map.get("image-id"));
    }
    
    @Test
    public void testOrderOfPreference() {
        Map<String, Object> allProperties = Maps.newHashMap();
        allProperties.putAll(newSampleProps());
        allProperties.putAll(newSampleNamedProps());
        Map<String, Object> map = JcloudsPropertiesFromEnv.getJcloudsPropertiesFromEnv(null, "cloudfirst", allProperties);
        Assert.assertEquals(map.get("provider"), "openstack-nova");
        Assert.assertEquals(map.get("identity"), "myId");
        Assert.assertEquals(map.get("credential"), "password");
        Assert.assertEquals(map.get("imageId"), "RegionOne/89992f53-6ef8-4933-a08e-e7cba5601fe8");
        Assert.assertEquals(map.get("securityGroups"), "universal");
        Assert.assertNull(map.get("image-id"));
    }

}
