package brooklyn.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadSubstitutersTest {

    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testSimpleSubstitution() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String pattern = "mykey1=${mykey1},mykey2=${mykey2}";
        String result = DownloadSubstituters.substitute(pattern, ImmutableMap.of("mykey1", "myval1", "mykey2", "myval2"));
        assertEquals(result, "mykey1=myval1,mykey2=myval2");
    }

    @Test
    public void testSubstitutionIncludesDefaultSubs() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String pattern = "version=${version},type=${type},simpletype=${simpletype}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName()));
    }

    @Test
    public void testSubstitutionDoesMultipleMatches() throws Exception {
        String simpleType = TestEntity.class.getSimpleName();
        String pattern = "simpletype=${simpletype},simpletype=${simpletype}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("simpletype=%s,simpletype=%s", simpleType, simpleType));
    }

    @Test
    public void testSubstitutionUsesEntityBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${entity.id}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSubstitutionUsesDriverBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${driver.entity.id}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSubstitutionUsesOverrides() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String pattern = "version=${version},mykey1=${mykey1}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("version", "overriddenversion", "mykey1", "myval1"));
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, "version=overriddenversion,mykey1=myval1");
    }

    @Test
    public void testThrowsIfUnmatchedSubstitutions() throws Exception {
        String pattern = "nothere=${nothere}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        try {
            String result = DownloadSubstituters.substitute(req, pattern);
            fail("Should have failed, but got "+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("${nothere}")) throw e;
        }
    }

    @Test
    public void testSubstituter() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        String baseurl = "version=${version},type=${type},simpletype=${simpletype}";
        Map<String,Object> subs = DownloadSubstituters.getBasicEntitySubstitutions(driver);
        DownloadTargets result = DownloadSubstituters.substituter(Functions.constant(baseurl), Functions.constant(subs)).apply(new BasicDownloadRequirement(driver));
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName());
        assertEquals(result.getPrimaryLocations(), ImmutableList.of(expected));
    }
}
