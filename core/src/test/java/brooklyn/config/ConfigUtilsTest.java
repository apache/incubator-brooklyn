package brooklyn.config;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.basic.ConfigKeys;

public class ConfigUtilsTest {

    public static final ConfigKey<String> S1 = ConfigKeys.newStringKey("s1");
    public final ConfigKey<String> S2 = ConfigKeys.newStringKey("s2");
    
    @Test
    public void testGetStaticKeys() {
        Set<HasConfigKey<?>> keys = ConfigUtils.getStaticKeysOnClass(ConfigUtilsTest.class);
        if (keys.size()!=1) Assert.fail("Expected 1 key; got: "+keys);
        Assert.assertEquals(keys.iterator().next().getConfigKey(), S1);
    }
}
