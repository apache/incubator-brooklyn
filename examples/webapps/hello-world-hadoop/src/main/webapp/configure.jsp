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
<h1>Sample Brooklyn Deployed WebApp</h1>
This is the output of a JSP page that is part of the Hello, World application,
deployed by brooklyn, to allow <b>configuration<b>.
</td>
</tr>
</table>

<%
if (request.getParameter("key")!=null) {
    String key = request.getParameter("key"); 
    String value = request.getParameter("value");
    Object oldValue = System.getProperties().setProperty(key, value);
%>
    <p><i>Applied new value for '<%= key %>'.</i></p>
<% } %>

<table border="1">

<tr>
<td><b>Key</b></td>
<td><b>Current/New Value</b></td>
<td><b>Sample Value</b></td>
</tr>

<tr>
<td>brooklyn.example.db.url</td>
<td>
<form action="configure.jsp" method="GET">
  <input type="hidden" name="key" value="brooklyn.example.db.url">
  <input type="text" name="value" value="<%= System.getProperty("brooklyn.example.db.url", "") %>" width="30">
  <input type="submit" value="Set"/>
</form>
</td>
<td>jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n</td>
</tr>

<tr>
<td>brooklyn.example.hadoop.site.xml.url</td>
<td>
<form action="configure.jsp" method="GET">
  <input type="hidden" name="key" value="brooklyn.example.hadoop.site.xml.url">
  <input type="text" name="value" value="<%= System.getProperty("brooklyn.example.hadoop.site.xml.url", "") %>" width="30">
  <input type="submit" value="Set"/>
</form>
</td>
<td>file:///tmp/hadoop-site.xml</td>
</tr>

<tr>
<td>brooklyn.example.hadoop.site.xml.contents</td>
<td>
<form action="configure.jsp" method="GET">
  <input type="hidden" name="key" value="brooklyn.example.hadoop.site.xml.contents">
  <textarea name="value" cols="40" rows="8"><%= System.getProperty("brooklyn.example.hadoop.site.xml.contents", "") %></textarea>
  <input type="submit" value="Set"/>
</form>
</td>
<td>
<pre>
&lt;configuration&gt;
  &lt;property&gt;
    &lt;name&gt;mapred.job.tracker&lt;/name&gt;
    &lt;value&gt;ec2-184-169-225-252.us-west-1.compute.amazonaws.com:8021&lt;/value&gt;
  &lt;/property&gt;
  &lt;property&gt;
    &lt;name&gt;fs.default.name&lt;/name&gt;
    &lt;value&gt;hdfs://ec2-184-169-225-252.us-west-1.compute.amazonaws.com:8020/&lt;/value&gt;
  &lt;/property&gt;
  &lt;property&gt;
    &lt;name&gt;hadoop.socks.server&lt;/name&gt;
    &lt;value&gt;localhost:6666&lt;/value&gt;
  &lt;/property&gt;
  &lt;property&gt;
    &lt;name&gt;fs.s3n.awsAccessKeyId&lt;/name&gt;
    &lt;value&gt;XXX&lt;/value&gt;
  &lt;property&gt;
    &lt;name&gt;fs.s3.awsSecretAccessKey&lt;/name&gt;
    &lt;value&gt;XXX&lt;/value&gt;
  &lt;/property&gt;
&lt;/configuration&gt;
</pre>
</td>
</tr>

</table>

<p>Click <a href="index.html">here</a> to go back to the main page.</p>

</body>
</html>
