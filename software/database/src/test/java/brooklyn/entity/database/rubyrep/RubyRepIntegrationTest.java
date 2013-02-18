package brooklyn.entity.database.rubyrep;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.database.mysql.MySqlIntegrationTest;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.database.mysql.MySqlNodeImpl;
import brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import brooklyn.entity.database.postgresql.PostgreSqlNode;
import brooklyn.entity.database.postgresql.PostgreSqlNodeImpl;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.util.MutableMap;

public class RubyRepIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(RubyRepIntegrationTest.class);
    protected TestApplication tapp;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        tapp = new TestApplicationImpl(MutableMap.of("name", "RubyRepTest"));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (tapp != null) Entities.destroy(tapp);
    }

    @Test(groups = "Integration")
    public void test_localhost_mysql() throws Exception {
        MySqlNode db1 = new MySqlNodeImpl(MutableMap.of("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT,
                "port", "9111"), tapp);

        MySqlNode db2 = new MySqlNodeImpl(MutableMap.of("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT,
                "port", "9112"), tapp);

        testInLocation(db1, db2, new LocalhostMachineProvisioningLocation());
    }

    @Test(enabled=false, groups = "Integration") // TODO had to alter kern.sysv.shmmax, kern.sysv.semmns etc to get this to pass on OS X
    public void test_localhost_postgres() throws Exception {
        PostgreSqlNode db1 = new PostgreSqlNodeImpl(MutableMap.of("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT,
                "port", "9111"), tapp);

        PostgreSqlNode db2 = new PostgreSqlNodeImpl(MutableMap.of("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT,
                "port", "9112"), tapp);

        testInLocation(db1, db2, new LocalhostMachineProvisioningLocation());
    }
    
    @Test(enabled=false, groups = "Integration") // TODO this doesn't appear to be supported by RubyRep
    public void test_localhost_postgres_mysql() throws Exception {
        PostgreSqlNode db1 = new PostgreSqlNodeImpl(MutableMap.of("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT,
                "port", "9111"), tapp);
        MySqlNode db2 = new MySqlNodeImpl(MutableMap.of("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT,
                "port", "9112"), tapp);

        testInLocation(db1, db2, new LocalhostMachineProvisioningLocation());
    }

    /**
     * Tests rubyrep with two databases
     */
    protected void testInLocation(DatabaseNode db1, DatabaseNode db2, Location... locations) throws Exception {
        Map rubyRepOptions = MutableMap.<String, Object>of(
                "left", db1,
                "right", db2,
                "leftUsername", "sqluser",
                "leftPassword", "sqluserpw",
                "rightUsername", "sqluser",
                "rightPassword", "sqluserpw")
                .add("leftDatabase", "feedback").add("rightDatabase", "feedback");

        RubyRep rep = new RubyRep(rubyRepOptions, tapp);

        Entities.startManagement(tapp);
        tapp.start(Arrays.asList(locations));

        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        SshMachineLocation l1 = (SshMachineLocation) db1.getLocations().iterator().next();
        SshMachineLocation l2 = (SshMachineLocation) db2.getLocations().iterator().next();
        l1.exec(asList("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"));
        l2.exec(asList("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"));

        log.info("RubyRep started");
        URI db1Url = URI.create(db1.getAttribute(DatabaseNode.DB_URL));
        URI db2Url = URI.create(db2.getAttribute(DatabaseNode.DB_URL));
        
        VogellaExampleAccess vea1 = new VogellaExampleAccess("org.postgresql.Driver", db1Url.getScheme(), db1.getAttribute(SoftwareProcess.HOSTNAME), db1Url.getPort());
        VogellaExampleAccess vea2 = new VogellaExampleAccess("org.postgresql.Driver", db2Url.getScheme(), db2.getAttribute(SoftwareProcess.HOSTNAME), db2Url.getPort());

        try {

            vea1.connect();
            List<List<String>> rs = vea1.readDataBase();
            assertEquals(rs.size(), 1);

            vea2.connect();
            rs = vea2.readDataBase();
            assertEquals(rs.size(), 1);

            log.info("Modifying left database");
            vea1.modifyDataBase();

            log.info("Sleeping for 40 seconds");

            Thread.sleep(40000);

            log.info("Reading left database");
            rs = vea1.readDataBase();
            assertEquals(rs.size(), 2);
            
            log.info("Reading right database");
            rs = vea2.readDataBase();
            assertEquals(rs.size(), 2);
        } finally {
            vea1.close();
            vea2.close();
        }
    }
}
