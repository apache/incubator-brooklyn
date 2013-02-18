package brooklyn.entity.database.rubyrep;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.database.mysql.MySqlIntegrationTest;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import brooklyn.entity.database.postgresql.PostgreSqlNode;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class RubyRepIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(RubyRepIntegrationTest.class);
    protected TestApplication tapp;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        tapp = ApplicationBuilder.builder(TestApplication.class).manage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (tapp != null) Entities.destroy(tapp);
        tapp = null;
    }

    @Test(groups = "Integration")
    public void test_localhost_mysql() throws Exception {
        MySqlNode db1 = tapp.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9111"));

        MySqlNode db2 = tapp.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9112"));


        startInLocation(tapp, db1, db2, new LocalhostMachineProvisioningLocation());
        testReplication(db1, db2);
    }

    @Test(groups = "Integration")
    // TODO had to alter kern.sysv.shmmax, kern.sysv.semmns etc to get this to pass on OS X
    public void test_localhost_postgres() throws Exception {
        PostgreSqlNode db1 = tapp.createAndManageChild(BasicEntitySpec.newInstance(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9113"));

        PostgreSqlNode db2 = tapp.createAndManageChild(BasicEntitySpec.newInstance(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9114"));

        startInLocation(tapp, db1, db2, new LocalhostMachineProvisioningLocation());
        testReplication(db1, db2);
    }

    @Test(enabled = false, groups = "Integration") // TODO this doesn't appear to be supported by RubyRep
    public void test_localhost_postgres_mysql() throws Exception {
        PostgreSqlNode db1 = tapp.createAndManageChild(BasicEntitySpec.newInstance(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9115"));

        MySqlNode db2 = tapp.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", "9116"));


        startInLocation(tapp, db1, db2, new LocalhostMachineProvisioningLocation());
        testReplication(db1, db2);
    }

    /**
     * Configures rubyrep to connect to the two databases and starts the app
     */
    public static void startInLocation(TestApplication tapp, DatabaseNode db1, DatabaseNode db2, Location... locations) throws Exception {
        tapp.createAndManageChild(BasicEntitySpec.newInstance(RubyRep.class)
                .configure("left", db1)
                .configure("right", db2)
                .configure("leftUsername", "sqluser")
                .configure("rightUsername", "sqluser")
                .configure("rightPassword", "sqluserpw")
                .configure("leftPassword", "sqluserpw")
                .configure("leftDatabase", "feedback")
                .configure("rightDatabase", "feedback")
                .configure("replicationInterval", 1)
        );

        tapp.start(Arrays.asList(locations));
    }

    /**
     * Tests replication between the two databases by altering the first and checking the change is applied to the second
     */
    public static void testReplication(DatabaseNode db1, DatabaseNode db2) throws Exception {
        URI db1Url = URI.create(db1.getAttribute(DatabaseNode.DB_URL));
        URI db2Url = URI.create(db2.getAttribute(DatabaseNode.DB_URL));

        log.info("Testing replication between " + db1Url + " and " + db2Url);

        VogellaExampleAccess vea1 = new VogellaExampleAccess(db1 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver",
                db1Url.getScheme(), db1Url.getHost(), db1Url.getPort());
        VogellaExampleAccess vea2 = new VogellaExampleAccess(db2 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver",
                db2Url.getScheme(), db1Url.getHost(), db2Url.getPort());

        try {
            vea1.connect();
            List<List<String>> rs = vea1.readDataBase();
            assertEquals(rs.size(), 1);

            vea2.connect();
            rs = vea2.readDataBase();
            assertEquals(rs.size(), 1);

            log.info("Modifying left database");
            vea1.modifyDataBase();

            log.info("Reading left database");
            rs = vea1.readDataBase();
            assertEquals(rs.size(), 2);

            log.info("Reading right database");
            rs = vea2.readDataBase();

            for (int i = 0; i < 60 && rs.size() != 2; i++) {
                log.info("Sleeping for a second");
                Thread.sleep(1000);
                rs = vea2.readDataBase();
            }

            assertEquals(rs.size(), 2);
        } finally {
            vea1.close();
            vea2.close();
        }
    }
}
