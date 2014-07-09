package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;

import com.google.common.base.CaseFormat;

public class ConfigKeysTest {

    @Test
    public void testConvertKeyToLowerHyphen() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("privateKeyFile", "my descr", "my default val");
        ConfigKey<String> key2 = ConfigKeys.convert(key, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
        
        assertEquals(key2.getName(), "private-key-file");
        assertEquals(key2.getType(), String.class);
        assertEquals(key2.getDescription(), "my descr");
        assertEquals(key2.getDefaultValue(), "my default val");
    }
    
    @Test
    public void testConvertKeyToCamelCase() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("private-key-file");
        ConfigKey<String> key2 = ConfigKeys.convert(key, CaseFormat.LOWER_HYPHEN, CaseFormat.LOWER_CAMEL);
        
        assertEquals(key2.getName(), "privateKeyFile");
    }
    
    @Test
    public void testConfigKeyWithPrefix() throws Exception {
        ConfigKey<String> key = ConfigKeys.newStringConfigKey("mykey", "my descr", "my default val");
        ConfigKey<String> key2 = ConfigKeys.newConfigKeyWithPrefix("a.b.", key);
        
        assertEquals(key2.getName(), "a.b.mykey");
        assertEquals(key2.getType(), String.class);
        assertEquals(key2.getDescription(), "my descr");
        assertEquals(key2.getDefaultValue(), "my default val");
    }
}
