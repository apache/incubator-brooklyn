<html>
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
String url=System.getProperty("brooklyn.example.db.url");
//URL should be supplied e.g. "-Dbrooklyn.example.db.url=jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
//(note quoting needed due to ampersand)
if (url!=null) {
%>
<li><a href="db.jsp">SQL database chatroom</a></li>
<% } %>

<%
String hadoopConfigUrl=System.getProperty("brooklyn.example.hadoop.site.xml.url");
String hadoopConfigContents=System.getProperty("brooklyn.example.hadoop.site.xml.contents");
boolean hasHadoop = (hadoopConfigUrl!=null || hadoopConfigContents!=null);
//URL or contents should be supplied e.g. -Dbrooklyn.example.hadoop.site.xml.url=file://tmp/hadoop-site.xml
if (hasHadoop) {
%>
<li><a href="hadoop-chat.jsp">Hadoop chatroom</a></li>
<li><a href="hadoop-wordcount.jsp">Hadoop wordcount</a> (inevitably!) run over the chats</li>
<% } %>

<%
if (!hasHadoop && url==null) {
%>
<li><i>None.</i> Try one of the other Brooklyn examples to see SQL or Hadoop.</li>
<% } %>
</ul>

<p>Or you can supply or edit the <a href="configure.jsp">configuration</a>.</p>

</body>
</html>
