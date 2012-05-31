package brooklyn.entity.database.mysql;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Runs the popular Vogella MySQL tutorial,
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class MySqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(MySqlIntegrationTest.class);
    
    @BeforeMethod(groups = [ "Integration" ])
    public void ensureNoInstance() {
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureShutDown() {
    }

    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT = """
create database feedback;
use feedback;
CREATE USER sqluser IDENTIFIED BY 'sqluserpw'; 
grant usage on *.* to sqluser@localhost identified by 'sqluserpw'; 
grant all privileges on feedback.* to sqluser@localhost;

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

    // FIXME Disabled on 2012-05-31 because failing in CC during "customization" phase
    @Test(groups = [ "Integration", "WIP" ])
    public void runIt() {
        TestApplication tapp = new TestApplication(name: "MySqlIntegrationTest");
        MySqlNode mysql = new MySqlNode(tapp, creationScriptContents: CREATION_SCRIPT);
        
        try {
            tapp.start([new LocalhostMachineProvisioningLocation()]);
            
            log.info("MySQL started");

            new VogellaExampleAccess().readDataBase("localhost", mysql.getPort());
            
            log.info("Ran vogella MySQL example -- SUCCESS");
        } finally {
            mysql.stop();
            tapp.destroy();
        }
    }

    static class VogellaExampleAccess {
        private Connection connect = null;
        private Statement statement = null;
        private PreparedStatement preparedStatement = null;
        private ResultSet resultSet = null;
    
        public void readDataBase(String host, int port) throws Exception {
            try {
                // This will load the MySQL driver, each DB has its own driver
                Class.forName("com.mysql.jdbc.Driver");
                // Setup the connection with the DB
                connect = DriverManager.getConnection(
                    "jdbc:mysql://${host}:${port}/feedback?"
                                + "user=sqluser&password=sqluserpw");
    
                // Statements allow to issue SQL queries to the database
                statement = connect.createStatement();
                // Result set get the result of the SQL query
                resultSet = statement.executeQuery("select * from FEEDBACK.COMMENTS");
                writeResultSet(resultSet);
    
                // PreparedStatements can use variables and are more efficient
                preparedStatement = connect.prepareStatement("insert into  FEEDBACK.COMMENTS values (default, ?, ?, ?, ? , ?, ?)");
                // "myuser, webpage, datum, summary, COMMENTS from FEEDBACK.COMMENTS");
                // Parameters start with 1
                preparedStatement.setString(1, "Test");
                preparedStatement.setString(2, "TestEmail");
                preparedStatement.setString(3, "TestWebpage");
                preparedStatement.setDate(4, new java.sql.Date(2009, 12, 11));
                preparedStatement.setString(5, "TestSummary");
                preparedStatement.setString(6, "TestComment");
                preparedStatement.executeUpdate();
    
                preparedStatement = connect.prepareStatement("SELECT myuser, webpage, datum, summary, COMMENTS from FEEDBACK.COMMENTS");
                resultSet = preparedStatement.executeQuery();
                writeResultSet(resultSet);
    
                // Remove again the insert comment
                preparedStatement = connect
                .prepareStatement("delete from FEEDBACK.COMMENTS where myuser= ? ; ");
                preparedStatement.setString(1, "Test");
                preparedStatement.executeUpdate();
                
                resultSet = statement.executeQuery("select * from FEEDBACK.COMMENTS");
                writeMetaData(resultSet);
                
            } catch (Exception e) {
                throw e;
            } finally {
                close();
            }
    
        }
    
        private void writeMetaData(ResultSet resultSet) throws SQLException {
            //  Now get some metadata from the database
            // Result set get the result of the SQL query
            
            log.info("The columns in the table are: ");
            
            log.info("Table: " + resultSet.getMetaData().getTableName(1));
            for  (int i = 1; i<= resultSet.getMetaData().getColumnCount(); i++){
                log.info("Column " +i  + " "+ resultSet.getMetaData().getColumnName(i));
            }
        }
    
        private void writeResultSet(ResultSet resultSet) throws SQLException {
            // ResultSet is initially before the first data set
            while (resultSet.next()) {
                // It is possible to get the columns via name
                // also possible to get the columns via the column number
                // which starts at 1
                // e.g. resultSet.getSTring(2);
                String user = resultSet.getString("myuser");
                String website = resultSet.getString("webpage");
                String summary = resultSet.getString("summary");
                Date date = resultSet.getDate("datum");
                String comment = resultSet.getString("comments");
                log.info("User: " + user);
                log.info("Website: " + website);
                log.info("Summary: " + summary);
                log.info("Date: " + date);
                log.info("Comment: " + comment);
            }
        }
    
        // You need to close the resultSet
        private void close() {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
    
                if (statement != null) {
                    statement.close();
                }
    
                if (connect != null) {
                    connect.close();
                }
            } catch (Exception e) {
    
            }
        }
    }    
}
