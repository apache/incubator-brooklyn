package brooklyn.entity.drivers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadResolversTest {

    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private final Function<Object, ImmutableMap<String, String>> emptyAdditionalSubs = Functions.constant(ImmutableMap.<String,String>of());

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication.class).manage();
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
        assertEquals(result, String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntityImpl.class.getName(), TestEntityImpl.class.getSimpleName()));
    }

    @Test
    public void testSimpleSubstitutionDoesMultipleMatches() throws Exception {
        String pattern = "simpletype=${simpletype},simpletype=${simpletype}";
        String result = DownloadResolvers.substitute(driver, pattern, emptyAdditionalSubs);
        assertEquals(result, String.format("simpletype=%s,simpletype=%s", TestEntityImpl.class.getSimpleName(), TestEntityImpl.class.getSimpleName()));
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
        List<String> result = DownloadResolvers.attributeSubstituter(Attributes.DOWNLOAD_URL).apply(driver);
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntityImpl.class.getName(), TestEntityImpl.class.getSimpleName());
        assertEquals(result, ImmutableList.of(expected));
    }
    
    @Test
    public void testDefaultResolverSubstitutesDownloadUrl() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "version=${version},type=${type},simpletype=${simpletype}");
        List<String> result = app.getManagementContext().getEntityDownloadsRegistry().resolve(driver);
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntityImpl.class.getName(), TestEntityImpl.class.getSimpleName());
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
