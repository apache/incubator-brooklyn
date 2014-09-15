<%@ page language="java" import="com.basho.riak.client.IRiakClient,com.basho.riak.client.RiakFactory,com.basho.riak.client.bucket.Bucket" %>
<%@ page import="com.basho.riak.client.convert.RiakKey" %>
<%@ page import="com.basho.riak.client.raw.http.HTTPClientConfig" %>
<%@ page import="com.basho.riak.client.raw.http.HTTPClusterConfig" %>
<%@ page import="org.joda.time.DateTime" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>

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

<%!
    static class Message {
        @RiakKey
        public String id;
        public String name;
        public String message;
        public DateTime dateTime;

        public Message() {}

        public Message(String name, String message) {
            this.name = name;
            this.message = message;
            this.dateTime = DateTime.now();
        }
    }
%>

<head>
    <title>Sample Application Riak JSP Page</title>
</head>
<body bgcolor="white">

<table border="0">
    <tr>
        <td align=center>
            <img src="images/bridge-small.png">
        </td>
        <td>
            <h1>Sample Brooklyn Deployed WebApp (Riak JSP)</h1>
            This is the output of a JSP page that is part of the Hello, World application,
            deployed by brooklyn, to show <b>Riak database interactivity</b>.
        </td>
    </tr>
</table>

<%
    String url = System.getProperty("brooklyn.example.riak.url");
    if (url == null) {
%>
<% } else { %>
<br/>

<p>Visitors:</p>
<ul>

    <%
        }

        String[] riakNodes = System.getProperty("brooklyn.example.riak.nodes").split(",");
        int maxConnections = 50;

        HTTPClusterConfig httpClusterConfig = new HTTPClusterConfig(maxConnections);
        HTTPClientConfig httpClientConfig = HTTPClientConfig.defaults();
        httpClusterConfig.addHosts(httpClientConfig, riakNodes);

        IRiakClient httpClient = RiakFactory.newClient(httpClusterConfig);

        // If the bucket already exists, createBucket simply fetches the bucket
        Bucket bucket = httpClient.createBucket("visitors").execute();

        if (request.getParameter("name") != null) {
            Message message = new Message(request.getParameter("name"), request.getParameter("message"));
            bucket.store(message).withoutFetch().returnBody(true).execute();
        }

        List<Message> messages = new ArrayList<Message>();

        for (String key : bucket.keys()) {
            messages.add(bucket.fetch(key, Message.class).execute());
        }

        Collections.sort(messages, new Comparator<Message>() {
            public int compare(Message m1, Message m2) {
                return m1.dateTime.compareTo(m2.dateTime);
            }
        });

        for (Message outMessage : messages) {
            %>
                <li><b><%=outMessage.name%></b>: <%=outMessage.message%></li>
            <%
        }

        httpClient.shutdown();

        if (messages.size() == 0) {
            %>
                <li><i>None</i></li>
            <%
        }
    %>

</ul>

<br/>

<p>Please enter a message:</p>

<form action="riak.jsp" method="GET">
    <table>
        <tr>
            <td>Name:</td>
            <td><input type="text" name="name"></td>
        </tr>
        <tr>
            <td>Message:</td>
            <td><input type="text" name="message"></td>
        </tr>
    </table>
    <input type="submit" value="Submit"/>
</form>

<br/>

<p>Click <a href="index.html">here</a> to go back to the main page.</p>
</body>
</html>
