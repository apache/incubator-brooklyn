package brooklyn.entity.database.postgresql;


import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import brooklyn.entity.database.VogellaExampleAccess

/**
 * Runs the popular Vogella MySQL tutorial against PostgreSQL
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class PostgreSqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlIntegrationTest.class);

    @BeforeMethod(groups = ["Integration"])
    public void ensureNoInstance() {
    }

    @AfterMethod(groups = ["Integration"])
    public void ensureShutDown() {
    }

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT = """
CREATE USER sqluser WITH PASSWORD 'sqluserpw'; 
create database feedback;

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
    public void runIt() {
        TestApplication tapp = new TestApplication(name: "PostgreSqlIntegrationTest");
        PostgreSqlNode pgsql = new PostgreSqlNode(tapp, creationScriptContents: CREATION_SCRIPT);

        try {
            tapp.start([new LocalhostMachineProvisioningLocation()]);

            log.info("PostgreSql started");

            new VogellaExampleAccess().readDataBase("org.postgresql.Driver", "postgresql", "localhost", pgsql.getPort());

            log.info("Ran vogella PostgreSql example -- SUCCESS");
        } finally {
            pgsql.stop();
            tapp.destroy();
        }
    }
}
