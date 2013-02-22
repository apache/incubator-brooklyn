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

    /*
     * TODO had to alter kern.sysv.shmmax, kern.sysv.semmns etc to get this to pass on OS X.
     * See http://willbryant.net/software/mac_os_x/postgres_initdb_fatal_shared_memory_error_on_leopard
     * 
     * The error you'll get in the log (from stderr of PostgreSqlSshDriver.customize()) is:
     *   DETAIL:  Failed system call was shmget(key=2, size=2138112, 03600).
     *   HINT:  This error usually means that PostgreSQL's request for a shared memory segment exceeded available memory or swap space, or exceeded your kernel's SHMALL parameter.  
     *          You can either reduce the request size or reconfigure the kernel with larger SHMALL.  
     *          To reduce the request size (currently 2138112 bytes), reduce PostgreSQL's shared memory usage, perhaps by reducing shared_buffers or max_connections.
     * 
     * You may also get an error about not being able to allocate semaphores - this can be caused by kern.sysv.semmns being set too low,
     * hence increasing this to 87381 (which is the default on OS X Snow Leopard).
     * 
     * Create a file /etc/sysctl.conf with the following content:
     *   kern.sysv.shmmax=4194304
     *   kern.sysv.shmmin=1
     *   kern.sysv.shmmni=32
     *   kern.sysv.shmseg=8
     *   kern.sysv.shmall=65536
     *   kern.sysv.shmmax=16777216
     *   kern.sysv.semmns=87381
     */
    @Test(groups = "Integration")
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
        tapp.createAndManageChild(BasicEntitySpec.newInstance(RubyRepNode.class)
                .configure("leftDatabase", db1)
                .configure("rightDatabase", db2)
                .configure("leftUsername", "sqluser")
                .configure("rightUsername", "sqluser")
                .configure("rightPassword", "sqluserpw")
                .configure("leftPassword", "sqluserpw")
                .configure("leftDatabaseName", "feedback")
                .configure("rightDatabaseName", "feedback")
                .configure("replicationInterval", 1)
        );

        tapp.start(Arrays.asList(locations));
    }

    /**
     * Tests replication between the two databases by altering the first and checking the change is applied to the second
     */
    public static void testReplication(DatabaseNode db1, DatabaseNode db2) throws Exception {
        String db1Url = db1.getAttribute(DatabaseNode.DB_URL);
        String db2Url = db2.getAttribute(DatabaseNode.DB_URL);

        log.info("Testing replication between " + db1Url + " and " + db2Url);

        VogellaExampleAccess vea1 = new VogellaExampleAccess(db1 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver",
                db1Url);
        VogellaExampleAccess vea2 = new VogellaExampleAccess(db2 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver",
                db2Url);

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
