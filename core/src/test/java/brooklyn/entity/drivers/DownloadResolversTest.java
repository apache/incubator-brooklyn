package brooklyn.entity.drivers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.DownloadsRegistry.DownloadTargets;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadResolversTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private final Function<Object, ImmutableMap<String, String>> emptyAdditionalSubs = Functions.constant(ImmutableMap.<String,String>of());

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // Disable local-repo; as that is not part of this test
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(DownloadLocalRepoResolver.LOCAL_REPO_ENABLED_PROPERTY, "false");
        managementContext = new LocalManagementContext(brooklynProperties);
        
        loc = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication.class).manage(managementContext);
        entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testSimpleSubstitution() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String pattern = "version=${version},type=${type},simpletype=${simpletype}";
        String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
        assertEquals(result, String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName()));
    }

    @Test
    public void testSimpleSubstitutionUsesEntityBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${entity.id}";
        String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSimpleSubstitutionUsesDriverBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${driver.entity.id}";
        String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSimpleSubstitutionDoesMultipleMatches() throws Exception {
        String pattern = "simpletype=${simpletype},simpletype=${simpletype}";
        String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
        assertEquals(result, String.format("simpletype=%s,simpletype=%s", TestEntity.class.getSimpleName(), TestEntity.class.getSimpleName()));
    }

    @Test
    public void testThrowsIfUnmatchedSubstitutions() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String pattern = "nothere=${nothere}";
        try {
            String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
            fail("Should have failed, but got "+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("${nothere}")) throw e;
        }
    }

    @Test
    public void testSubstitutesAttributeValue() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "version=${version},type=${type},simpletype=${simpletype}");
        DownloadTargets result = DownloadResolvers.attributeSubstituter(Attributes.DOWNLOAD_URL).apply(driver);
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName());
        assertEquals(result.getPrimaryLocations(), ImmutableList.of(expected));
    }
    
    @Test
    public void testDefaultResolverSubstitutesDownloadUrl() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "version=${version},type=${type},simpletype=${simpletype}");
        List<String> result = app.getManagementContext().getEntityDownloadsRegistry().resolve(driver);
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName());
        assertEquals(result, ImmutableList.of(expected));
    }
    
    @Test
    public void testDefaultResolverSubstitutesDownloadUrlFailsIfVersionMissing() throws Exception {
        entity.setAttribute(Attributes.DOWNLOAD_URL, "version=${version}");
        try {
            List<String> result = app.getManagementContext().getEntityDownloadsRegistry().resolve(driver);
            fail("Should have failed, but got "+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("${version}")) throw e;
        }
    }
}
