<%@ page language="java" import="java.sql.*"%>

<html>
<head>
    <title>Sample Application MongoDB JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
    <tr>
        <td align=center>
            <img src="images/bridge-small.png">
        </td>
        <td>
            <h1>Sample Brooklyn Deployed WebApp (MongoDB JSP)</h1>
            This is the output of a JSP page that is part of the Hello, World application,
            deployed by brooklyn, to show <b>MongoDB database interactivity</b>.
        </td>
    </tr>
</table>

<%
String port=System.getProperty("brooklyn.example.mongodb.port");
//URL should be supplied e.g. ""-Dbrooklyn.example.db.url=jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
//(note quoting needed due to ampersand)
if (port==null) {
%>
<p>(There is no database included as part of this example.)</p>
<% } else { %>
<p>The port is: <%= port %></p>
<%
/* begin database-enabled block */ }
%>


