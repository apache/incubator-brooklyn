package brooklyn.config;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;

public class ConfigUtilsTest {

    public static final StringConfigKey S1 = new StringConfigKey("s1");
    public final StringConfigKey S2 = new StringConfigKey("s2");
    
    @Test
    public void testGetStaticKeys() {
        Set<HasConfigKey<?>> keys = ConfigUtils.getStaticKeysOnClass(ConfigUtilsTest.class);
        if (keys.size()!=1) Assert.fail("Expected 1 key; got: "+keys);
        Assert.assertEquals(keys.iterator().next().getConfigKey(), S1);
    }
}
