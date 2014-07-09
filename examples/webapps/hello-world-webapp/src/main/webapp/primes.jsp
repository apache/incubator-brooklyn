<%@ page language="java"%>

<html>
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
