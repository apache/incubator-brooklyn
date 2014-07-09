<%@ page language="java"%>

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
<title>Sample Application Compute JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
<tr>
<td align=center>
<img src="images/bridge-small.png">
</td>
<td>
<h1>Sample Brooklyn Deployed WebApp (Compute Primes JSP)</h1>
This is the output of a JSP page that is part of the Hello, World application,
deployed by brooklyn, to show <b>compute load by generating primes</b>.
</td>
</tr>
</table>

<%
Object nx = request.getParameter("n");
if (nx==null) {
%>
    <p>Please supply a value <i>n</i> in the URL, to compute primes up to <i>n</i>.</p>
    <form action="primes.jsp" method="GET">
      <table>
        <tr><td>Count primes up to: </td><td><input type="text" name="n"></td></tr>
      </table>
      <input type="submit" value="Submit"/>
    </form>
    
<%
} else {
    long n = Long.parseLong(""+nx);
    long startTime = System.currentTimeMillis();
    int count = 0;
    for (long k=2; k<=n; k++) {
        // check if k prime
        boolean prime = true;
        for (long j=2; j*j<=k; j++) {
            if (k%j==0) {
                prime = false;
                break;
            }
        }
        if (prime) count++;
    }
    long totalTime = System.currentTimeMillis() - startTime;
%>
    <p>There are <%= count %> primes less than or equal to <%= n %>.</p>
    <p>Computation took <%= totalTime %> ms.</p>
<%
}
%>

</body>
</html>
