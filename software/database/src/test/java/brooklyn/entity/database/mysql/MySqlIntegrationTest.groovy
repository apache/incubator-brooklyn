package brooklyn.entity.database.mysql

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert;
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.VogellaExampleAccess
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.text.Strings;

/**
 * Runs a slightly modified version of the popular Vogella MySQL tutorial,
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class MySqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(MySqlIntegrationTest.class);
    TestApplication tapp

    @BeforeMethod(alwaysRun = true)
    public void before() {
        tapp = ApplicationBuilder.builder(TestApplication.class).manage();
    }

    @AfterMethod(alwaysRun=true)
    public void ensureShutDown() {
        if (tapp != null) {
            Entities.destroyAll(tapp)
            tapp = null;
        };
    }

    // can start in AWS by running this -- or use brooklyn CLI/REST for most clouds, or programmatic/config for set of fixed IP machines

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT = """
create database feedback;
use feedback;
CREATE USER sqluser@'localhost' IDENTIFIED BY 'sqluserpw';
grant usage on *.* to sqluser@localhost identified by 'sqluserpw';
grant all privileges on feedback.* to sqluser@localhost;
CREATE USER sqluser@'%' IDENTIFIED BY 'sqluserpw';
grant all privileges on feedback.* to 'sqluser'@'%' identified by 'sqluserpw';
FLUSH PRIVILEGES;
CREATE TABLE COMMENTS (
        id INT NOT NULL AUTO_INCREMENT, 
        MYUSER VARCHAR(30) NOT NULL,
        EMAIL VARCHAR(30), 
        WEBPAGE VARCHAR(100) NOT NULL, 
        DATUM DATE NOT NULL, 
        SUMMARY VARCHAR(40) NOT NULL,
        COMMENTS VARCHAR(400) NOT NULL,
        PRIMARY KEY (ID)
    );

INSERT INTO COMMENTS values (default, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );
""";

    @Test(groups = ["Integration"])
    public void test_localhost() throws Exception {
        String dataDir = "/tmp/mysql-data-" + Strings.makeRandomId(8);
        MySqlNode mysql = tapp.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT)
                .configure("dataDir", dataDir));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        
        tapp.start([location]);
        log.info("MySQL started");

        new VogellaExampleAccess().readDataBase("com.mysql.jdbc.Driver", "mysql", "localhost", mysql.getAttribute(MySqlNode.MYSQL_PORT));

        log.info("Ran vogella MySQL example -- SUCCESS");

        // Ensure the data directory was successfully overridden.
        File dataDirFile = new File(dataDir);
        File mysqlSubdirFile = new File(dataDirFile, "mysql");
        Assert.assertTrue(mysqlSubdirFile.exists());

        // Clean up.
        dataDirFile.deleteDir();
    }
}
