package brooklyn.entity.drivers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class DownloadPropertiesResolverTest {

    // FIXME Needs to work with TestEntity instead of TestEntityImpl
    
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private BrooklynProperties config;
    DownloadPropertiesResolver resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        
        config = BrooklynProperties.Factory.newEmpty();
        resolver = new DownloadPropertiesResolver(config);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testReturnsNullWhenEmpty() throws Exception {
        assertNull(resolver.apply(driver));
    }
    
    @Test
    public void testReturnsGlobalUrl() throws Exception {
        config.put("brooklyn.downloads.all.url", "myurl");
        assertResolves("myurl");
    }
    
    @Test
    public void testReturnsGlobalUrlsSplitOnSemicolon() throws Exception {
        config.put("brooklyn.downloads.all.url", "myurl; myurl2");
        assertResolves("myurl", "myurl2");
    }
    
    @Test
    public void testReturnsGlobalFallbackUrl() throws Exception {
        config.put("brooklyn.downloads.all.fallbackurl", "myurl");
        assertResolves("myurl");
    }

    @Test
    public void testSubstitutionsAppliedToFallbackUrl() throws Exception {
        config.put("brooklyn.downloads.all.fallbackurl", "foo=${foo},version=${version}");
        config.put("brooklyn.downloads.all.substitutions.foo", "myfoo");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("foo=myfoo,version=myversion");
    }

    @Test
    public void testReturnsGlobalFallbackUrlAsLast() throws Exception {
        config.put("brooklyn.downloads.all.url", "myurl");
        config.put("brooklyn.downloads.all.fallbackurl", "myurl2");
        assertResolves("myurl", "myurl2");
    }
    
    @Test
    public void testReturnsGlobalUrlWithEntitySubstituions() throws Exception {
        config.put("brooklyn.downloads.all.url", "version=${version}");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("version=myversion");
    }
    
    @Test
    public void testGlobalSubstitutionsAppliedToGlobalUrl() throws Exception {
        config.put("brooklyn.downloads.all.url", "foo=${foo},version=${version}");
        config.put("brooklyn.downloads.all.substitutions.foo", "myfoo");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("foo=myfoo,version=myversion");
    }
    
    @Test
    public void testGlobalSubstitutionsOverrideDefaults() throws Exception {
        config.put("brooklyn.downloads.all.url", "version=${version}");
        config.put("brooklyn.downloads.all.substitutions.version", "myoverriddenversion");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("version=myoverriddenversion");
    }
    
    @Test
    public void testGlobalSubstitutionsAppliedToDefaultUrl() throws Exception {
        config.put("brooklyn.downloads.all.substitutions.foo", "myfoo");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "foo=${foo},version=${version}");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("foo=myfoo,version=myversion");
    }
    
    @Test
    public void testEntitySpecificUrlOverridesGlobalUrl() throws Exception {
        config.put("brooklyn.downloads.all.url", "version=${version}");
        config.put("brooklyn.downloads.entity.TestEntityImpl.url", "overridden,version=${version}");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("overridden,version=myversion");
    }
    
    @Test
    public void testEntitySpecificSubstitutionsOverridesDefaults() throws Exception {
        config.put("brooklyn.downloads.entity.TestEntityImpl.url", "version=${version}");
        config.put("brooklyn.downloads.entity.TestEntityImpl.substitutions.version", "myoverriddenversion");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("version=myoverriddenversion");
    }
    
    private void assertResolves(String... expected) {
        assertEquals(resolver.apply(driver), ImmutableList.copyOf(expected));
    }
}
