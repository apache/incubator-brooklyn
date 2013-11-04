package brooklyn.policy.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * Test that configuration properties are usable and inherited correctly.
 */
public class EnricherConfigTest {
    
    // TODO These tests are a copy of PolicyConfigMapUsageTest, which is a code smell.
    // However, the src/main/java code does not contain as much duplication.
    
    public static class MyEnricher extends AbstractEnricher {
        @SetFromFlag("intKey")
        public static final BasicConfigKey<Integer> INT_KEY = new BasicConfigKey<Integer>(Integer.class, "bkey", "b key");
        
        @SetFromFlag("strKey")
        public static final ConfigKey<String> STR_KEY = new BasicConfigKey<String>(String.class, "akey", "a key");
        public static final ConfigKey<Integer> INT_KEY_WITH_DEFAULT = new BasicConfigKey<Integer>(Integer.class, "ckey", "c key", 1);
        public static final ConfigKey<String> STR_KEY_WITH_DEFAULT = new BasicConfigKey<String>(String.class, "strKey", "str key", "str key default");
        
        MyEnricher(Map flags) {
            super(flags);
        }
        
        MyEnricher() {
            super();
        }
    }
    
    private BasicConfigKey<String> differentKey = new BasicConfigKey<String>(String.class, "differentkey", "diffval");

    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testConfigFlagsPassedInAtConstructionIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put("strKey", "aval")
                .put("intKey", 2)
                .build());
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
        // this is set, because key name matches annotation on STR_KEY
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), "aval");
    }
    
    @Test
    public void testUnknownConfigPassedInAtConstructionIsWarnedAndIgnored() throws Exception {
        // TODO Also assert it's warned
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(differentKey, "aval")
                .build());
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(differentKey), null);
        assertEquals(enricher.getEnricherType().getConfigKey(differentKey.getName()), null);
    }
    
    @Test
    public void testConfigPassedInAtConstructionIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "aval")
                .put(MyEnricher.INT_KEY, 2)
                .build());
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
        // this is not set (contrast with above)
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), MyEnricher.STR_KEY_WITH_DEFAULT.getDefaultValue());
    }
    
    @Test
    public void testConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.INT_KEY_WITH_DEFAULT, 0)
                .build());
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY_WITH_DEFAULT), (Integer)0);
    }
    
    @Test
    public void testConfigSetToNullIsAvailable() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY_WITH_DEFAULT, null)
                .build());
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), null);
    }
    
    @Test
    public void testConfigCanBeSetOnEnricher() throws Exception {
        MyEnricher enricher = new MyEnricher();
        enricher.setConfig(MyEnricher.STR_KEY, "aval");
        enricher.setConfig(MyEnricher.INT_KEY, 2);
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "aval");
        assertEquals(enricher.getConfig(MyEnricher.INT_KEY), (Integer)2);
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "aval")
                .build());
        enricher.setConfig(MyEnricher.STR_KEY, "diffval");
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "diffval");
    }

    @Test
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        MyEnricher enricher = new MyEnricher(MutableMap.builder()
                .put(MyEnricher.STR_KEY, "origval")
                .build());
        app.addEnricher(enricher);
        
        try {
            enricher.setConfig(MyEnricher.STR_KEY,"newval");
            fail();
        } catch (UnsupportedOperationException e) {
            // success
        }
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY), "origval");
    }
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        MyEnricher enricher = new MyEnricher();
        app.addEnricher(enricher);
        
        assertEquals(enricher.getConfig(MyEnricher.STR_KEY_WITH_DEFAULT), "str key default");
    }
}
