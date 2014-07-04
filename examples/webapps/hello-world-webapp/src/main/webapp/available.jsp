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
<title>Sample "Hello, World" Application</title>
</head>
<body bgcolor=white>

<table border="0">
<tr>
<td>
<img src="images/bridge-small.png">
</td>
<td>
<h1>Sample Brooklyn Deployed "Hello, World" Application</h1>
<p>This is the home page for a sample application used to illustrate 
how web applications can be deployed to multi-cloud environments using Brooklyn.
</td>
</tr>
</table>

<p>
The following apps are available:
<p>

<ul>
<%
String dburl=System.getProperty("brooklyn.example.db.url");
//URL should be supplied e.g. "-Dbrooklyn.example.db.url=jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
//(note quoting needed due to ampersand)
if (dburl!=null) {
%>
<li><a href="db.jsp">SQL database chatroom</a></li>
<% } %>

<%
String hadoop=System.getProperty("brooklyn.example.hadoop.site.xml.url");
//URL should be supplied e.g. -Dbrooklyn.example.hadoop.site.xml.url=file://tmp/hadoop-site.xml
if (hadoop!=null) {
%>
<li><a href="hadoop-chat.jsp">Hadoop chatroom</a></li>
<li><a href="hadoop-wordcount.jsp">Hadoop wordcount</a> (inevitably!) run over the chats</li>
<% } %>

<%
boolean primes = true;
if (primes) {
%>
<li><a href="primes.jsp">Prime number counting (compute)</a></li>
<% } %>

<%
if (hadoop==null && dburl==null && primes==false) {
%>
<li><i>None.</i> Try one of the other Brooklyn examples to see SQL or Hadoop.</li>
<% } %>
</ul>

</body>
</html>
