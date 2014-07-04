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
<title>Sample Application HDFS JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
<tr>
<td align=center>
<img src="images/bridge-small.png">
</td>
<td>
<h1>Sample Brooklyn Deployed WebApp (Hadoop JSP)</h1>
This is the output of a JSP page that is part of the Hello, World application,
deployed by brooklyn, to show <b>hadoop hdfs used as the data store</b>.
</td>
</tr>
</table>

<%
String hadoopConfigUrl=System.getProperty("brooklyn.example.hadoop.site.xml.url");
String hadoopConfigContents=System.getProperty("brooklyn.example.hadoop.site.xml.contents");
if (hadoopConfigUrl==null && hadoopConfigContents==null) {
%>
    <p>There is no hadoop included as part of this example. Set brooklyn.example.hadoop.site.xml.url 
    or brooklyn.example.hadoop.site.xml.contents to proceed.</p>
<% } else { /* begin hadoop-enabled block */ 

try {
    org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
    conf.setClassLoader(brooklyn.demo.webapp.hello.HadoopWordCount.class.getClassLoader());
    if (hadoopConfigUrl!=null) conf.addResource(new java.net.URL(hadoopConfigUrl));
    if (hadoopConfigContents!=null) conf.addResource(new java.io.ByteArrayInputStream(hadoopConfigContents.getBytes()));
    org.apache.hadoop.fs.FileSystem fsClient = org.apache.hadoop.fs.FileSystem.get(conf);
    if (fsClient==null) throw new NullPointerException("Can't access fsClient");
    
    if (request.getParameter("name")!=null) {
        java.io.OutputStream os1 = fsClient.create(new org.apache.hadoop.fs.Path("chats", ""+System.currentTimeMillis()+"-"+((int)(Math.random()*10000))));
        os1.write(request.getParameter("name").getBytes());
        os1.write(" : ".getBytes());
        os1.write(request.getParameter("message").getBytes());
        os1.flush(); os1.close();
    }
%>
    <br/>
    <p>Visitors:</p>
    <ul>
<%
    org.apache.hadoop.fs.FileStatus[] files = fsClient.listStatus(new org.apache.hadoop.fs.Path("chats"));
    if (files==null) files = new org.apache.hadoop.fs.FileStatus[0]; 
    for (org.apache.hadoop.fs.FileStatus f: files) {
%>
        <li> <%= brooklyn.demo.webapp.hello.DemoUtils.stringFromInputStream(fsClient.open(f.getPath())) %> </li>
<%
    }
    if (files.length==0) {
    %>
      <li> <i>None.</i> </li>
    <%
    }
    %>

    </ul>
    <br/>

    <p>Please enter a message:</p>

    <form action="hadoop-chat.jsp" method="GET">
      <table>
        <tr><td>Name: </td><td><input type="text" name="name"></td></tr>
        <tr><td>Message: </td><td><input type="text" name="message"></td></tr>
      </table>
      <input type="submit" value="Submit"/>
    </form>

<%
} catch (Exception e) {
%>
    <b>Error connecting to Hadoop.</b><br/>
    ERROR: <%= e %><br/><br/>
    <pre> <%= brooklyn.demo.webapp.hello.DemoUtils.getStackTrace(e) %></pre>
<%
}
}
%>

    <br/>
    <p>Click <a href="hadoop-wordcount.jsp">here</a> to run a map-reduce job counting the words in these comments.</p>
    <p>Click <a href="index.html">here</a> to go back to the main page.</p>

</body>
</html>
