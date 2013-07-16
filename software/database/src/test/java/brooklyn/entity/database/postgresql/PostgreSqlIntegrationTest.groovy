package brooklyn.entity.database.postgresql

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.VogellaExampleAccess
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.management.ManagementContext
import brooklyn.management.internal.LocalManagementContext
import brooklyn.test.entity.TestApplication

/**
 * Runs the popular Vogella MySQL tutorial against PostgreSQL
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class PostgreSqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlIntegrationTest.class);
    
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext managementContext;
    protected TestApplication tapp;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        tapp = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun = true)
    public void ensureShutDown() {
        if (tapp != null) {
            Entities.destroyAll(tapp)
            tapp = null;
        };
    }

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT = """
CREATE USER sqluser WITH PASSWORD 'sqluserpw'; 
CREATE DATABASE feedback OWNER sqluser;

\\c feedback;

CREATE TABLE COMMENTS (
        id INT8 NOT NULL, 
        MYUSER VARCHAR(30) NOT NULL,
        EMAIL VARCHAR(30), 
        WEBPAGE VARCHAR(100) NOT NULL, 
        DATUM DATE NOT NULL, 
        SUMMARY VARCHAR(40) NOT NULL,
        COMMENTS VARCHAR(400) NOT NULL,
        PRIMARY KEY (ID)
    );

GRANT ALL ON comments TO sqluser;

INSERT INTO COMMENTS values (1, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );
""";

    @Test(groups = ["Integration"])
    public void test_localhost() throws Exception {
        PostgreSqlNode pgsql = tapp.createAndManageChild(EntitySpecs.spec(PostgreSqlNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT)
                .configure("port", "9111"));

        tapp.start([new LocalhostMachineProvisioningLocation()]);
        log.info("PostgreSql started");
        new VogellaExampleAccess("org.postgresql.Driver", pgsql.getAttribute(PostgreSqlNode.DB_URL)).readModifyAndRevertDataBase();
        log.info("Ran vogella PostgreSql example -- SUCCESS");
    }
}
