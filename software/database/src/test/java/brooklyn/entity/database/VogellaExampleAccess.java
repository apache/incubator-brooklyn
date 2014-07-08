/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.database;

import com.beust.jcommander.internal.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;

/**
 * Basic JDBC Access test Class, based on the Vogella MySQL tutorial
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class VogellaExampleAccess {
    public static final Logger log = LoggerFactory.getLogger(VogellaExampleAccess.class);

    private Connection connect = null;
    private Statement statement = null;
    private final String url;
    private final String dbName;

    public VogellaExampleAccess(String driverClass, String url) throws ClassNotFoundException {
        this(driverClass, url, "feedback");
    }
    
    public VogellaExampleAccess(String driverClass, String url, String dbName) throws ClassNotFoundException {
        // This will load the JDBC driver, each DB has its own driver
        Class.forName(driverClass);
        this.url = url;
        this.dbName = dbName;
    }

    public void readModifyAndRevertDataBase() throws Exception {
        connect();
        readDataBase();
        modifyDataBase();
        revertDatabase();
        close();
    }

    public void connect() throws Exception {
        try {
            // Setup the connection with the DB
            String jdbcUrl = "jdbc:" + url + dbName + "?" + "user=sqluser&password=sqluserpw";
            log.info("Connecting to " + jdbcUrl);
            connect = DriverManager.getConnection(jdbcUrl);

            // Statements allow to issue SQL queries to the database
            statement = connect.createStatement();
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    public List<List<String>> readDataBase() throws Exception {
        List<List<String>> results = Lists.newArrayList();
        // Result set get the result of the SQL query
        ResultSet resultSet = statement.executeQuery("SELECT myuser, webpage, datum, summary, COMMENTS from COMMENTS");
        // ResultSet is initially before the first data set
        while (resultSet.next()) {
            List<String> row = Lists.newArrayList();
            for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                row.add(resultSet.getObject(i).toString());
            }
            results.add(row);
        }
        // Should close resultsets
        resultSet.close();
        writeResultSet(results);
        return results;
    }

    public void modifyDataBase() throws Exception {
        // PreparedStatements can use variables and are more efficient
        PreparedStatement preparedStatement = connect.prepareStatement("insert into  COMMENTS values (?, ?, ?, ?, ? , ?, ?)");
        // "myuser, webpage, datum, summary, COMMENTS from FEEDBACK.COMMENTS");
        // Parameters start with 1
        preparedStatement.setInt(1, 2);
        preparedStatement.setString(2, "Test");
        preparedStatement.setString(3, "TestEmail");
        preparedStatement.setString(4, "TestWebpage");
        preparedStatement.setDate(5, new Date(new java.util.Date().getTime()));
        preparedStatement.setString(6, "TestSummary");
        preparedStatement.setString(7, "TestComment");
        preparedStatement.executeUpdate();

        writeResultSet(readDataBase());
    }

    // Remove again the insert comment added by modifyDataBase()
    public void revertDatabase() throws Exception {
        PreparedStatement preparedStatement = connect
                .prepareStatement("delete from COMMENTS where myuser= ? ; ");
        preparedStatement.setString(1, "Test");
        preparedStatement.executeUpdate();

        ResultSet resultSet = statement.executeQuery("select * from COMMENTS");
        writeMetaData(resultSet);
        // Should close resultsets
        resultSet.close();
    }

    private void writeMetaData(ResultSet resultSet) throws SQLException {
        // Get some metadata from the database
        log.info("The columns in the table are: ");

        log.info("Table: " + resultSet.getMetaData().getTableName(1));
        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
            log.info("Column " + i + " " + resultSet.getMetaData().getColumnName(i));
        }
    }

    private void writeResultSet(List<List<String>> resultSet) throws SQLException {
        for (List<String> row : resultSet) {
            String user = row.get(0);
            String website = row.get(1);
            String date = row.get(2);
            String summary = row.get(3);
            String comment = row.get(4);
            log.info("User: " + user);
            log.info("Website: " + website);
            log.info("Summary: " + summary);
            log.info("Date: " + date);
            log.info("Comment: " + comment);
        }
    }

    // You should always close the statement and connection
    public void close() throws Exception {
        if (statement != null) {
            statement.close();
            statement = null;
        }

        if (connect != null) {
            connect.close();
            connect = null;
        }
    }
}    