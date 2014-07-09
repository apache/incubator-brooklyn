<%@ page language="java" import="java.sql.*"%>

<html>
<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->
<head>
<title>Sample Application Database JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
<tr>
<td align=center>
<img src="images/bridge-small.png">
</td>
<td>
<h1>Sample Brooklyn Deployed WebApp (Database JSP)</h1>
This is the output of a JSP page that is part of the Hello, World application,
deployed by brooklyn, to show <b>SQL database interactivity</b>.
</td>
</tr>
</table>

<%
String url=System.getProperty("brooklyn.example.db.url");
//URL should be supplied e.g. ""-Dbrooklyn.example.db.url=jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
//(note quoting needed due to ampersand)
if (url==null) {
%>
    <p>(There is no database included as part of this example.)</p>
<% } else { /* begin database-enabled block */ %>

<br/>
<p>Visitors:</p>
<ul>

<%
Connection con=null;
ResultSet rst=null;
Statement stmt=null;
int i=0;

try {
  
  String DRIVER = "com.mysql.jdbc.Driver";
  Class.forName(DRIVER).newInstance();

  con=DriverManager.getConnection(url);
  stmt=con.createStatement();
  
  if (request.getParameter("name")!=null) {
      //add a message
      stmt.execute("INSERT INTO MESSAGES values (default, '"+
              //better escaping and security desired... 
              //this essentially does StringEscapeUtils.escapeSql (either brooklyn.util or apache commons)
              request.getParameter("name").replaceAll("'", "''")+
              "', '"+
              request.getParameter("message").replaceAll("'", "''")+
              "')");
  }
  
  rst=stmt.executeQuery("select * from MESSAGES");
  while (rst.next()) {
%>
    <li> <b><%= rst.getString(2) %></b>: <%= rst.getString(3) %> </li>
<%
    i++;
  }
} catch (Exception e) {
  i=-1;
%>
  <li> <b>The database does not appear to be connected.</b> </li>
  <li> ERROR: <%= e %> </li>
<%
} finally {
  if (rst!=null) rst.close();
  if (stmt!=null) stmt.close();
  if (con!=null) con.close();
}

if (i==0) {
%>
  <li> <i>None.</i> </li>
<%
}
%>

</ul>

<br/>

<p>Please enter a message:</p>

<form action="db.jsp" method="GET">
  <table>
    <tr><td>Name: </td><td><input type="text" name="name"></td></tr>
    <tr><td>Message: </td><td><input type="text" name="message"></td></tr>
  </table>
  <input type="submit" value="Submit"/>
</form>

<% } /* end of database-enabled block */ %>

<br/>
<p>Click <a href="index.html">here</a> to go back to the main page.</p>

</html>
