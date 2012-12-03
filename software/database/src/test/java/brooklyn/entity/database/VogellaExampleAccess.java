package brooklyn.entity.database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slightly altered version of the popular Vogella MySQL tutorial
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class VogellaExampleAccess {
    public static final Logger log = LoggerFactory.getLogger(VogellaExampleAccess.class);

    private Connection connect = null;
    private Statement statement = null;
    private PreparedStatement preparedStatement = null;
    private ResultSet resultSet = null;

    public void readDataBase(String driverClass, String type, String host, int port) throws Exception {
        try {
            // This will load the MySQL driver, each DB has its own driver
            Class.forName(driverClass);
            // Setup the connection with the DB
            connect = DriverManager.getConnection(
                    "jdbc:" + type + "://" + host + ":" + port + "/feedback?"
                            + "user=sqluser&password=sqluserpw");

            // Statements allow to issue SQL queries to the database
            statement = connect.createStatement();
            // Result set get the result of the SQL query
            resultSet = statement.executeQuery("select * from COMMENTS");
            writeResultSet(resultSet);

            // PreparedStatements can use variables and are more efficient
            preparedStatement = connect.prepareStatement("insert into  COMMENTS values (?, ?, ?, ?, ? , ?, ?)");
            // "myuser, webpage, datum, summary, COMMENTS from FEEDBACK.COMMENTS");
            // Parameters start with 1
            preparedStatement.setInt(1, 2);
            preparedStatement.setString(2, "Test");
            preparedStatement.setString(3, "TestEmail");
            preparedStatement.setString(4, "TestWebpage");
            preparedStatement.setDate(5, new java.sql.Date(2009, 12, 11));
            preparedStatement.setString(6, "TestSummary");
            preparedStatement.setString(7, "TestComment");
            preparedStatement.executeUpdate();

            preparedStatement = connect.prepareStatement("SELECT myuser, webpage, datum, summary, COMMENTS from COMMENTS");
            resultSet = preparedStatement.executeQuery();
            writeResultSet(resultSet);

            // Remove again the insert comment
            preparedStatement = connect
                    .prepareStatement("delete from COMMENTS where myuser= ? ; ");
            preparedStatement.setString(1, "Test");
            preparedStatement.executeUpdate();

            resultSet = statement.executeQuery("select * from COMMENTS");
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
        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
            log.info("Column " + i + " " + resultSet.getMetaData().getColumnName(i));
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