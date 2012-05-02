<%@ page language="java" import="java.sql.*"%>

<html>
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
<h1>Sample Brooklyn Deployed WebApp (Hadoop JSP)</h1>
This is the output of a JSP page that is part of the Hello, World application,
deployed by brooklyn, to show <b>a hadoop map-reduce task run<b>.
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

    org.apache.hadoop.mapreduce.Job job = brooklyn.demo.webapp.hello.HadoopWordCount.makeJob(conf);
    ((org.apache.hadoop.mapred.JobConf)job.getConfiguration()).setJar(brooklyn.demo.webapp.hello.SerializeHelloWorldHadoopJar.getJarName());
    org.apache.hadoop.fs.FileStatus[] files = fsClient.listStatus(new org.apache.hadoop.fs.Path("chats"));
    if (files==null) files = new org.apache.hadoop.fs.FileStatus[0];
    for (org.apache.hadoop.fs.FileStatus f: files) {
        org.apache.hadoop.mapreduce.lib.input.FileInputFormat.addInputPath(job, f.getPath());
    }
    
    org.apache.hadoop.fs.Path outp = new org.apache.hadoop.fs.Path("out-"+((int)(Math.random()*10000)));
    org.apache.hadoop.mapreduce.lib.output.FileOutputFormat.setOutputPath(job, outp);

    boolean result = job.waitForCompletion(true);
    
    if (!result) {
        %> <p><b></b><i>Map reduce job returned non-zero result code.</i></b></p> <%
    }

    %> <p>Output from map reduce is as follows:</p> <%
    
    files = fsClient.listStatus(outp);
    if (files==null) files = new org.apache.hadoop.fs.FileStatus[0];
    for (org.apache.hadoop.fs.FileStatus f: files) {
        try {
            if (!f.isDir() && f.getLen()>0) {
                %>
                <%= f.getPath() %>:<br/>
                <pre><%= brooklyn.demo.webapp.hello.DemoUtils.stringFromInputStream(fsClient.open(f.getPath())) %></pre>
                <%
            }
        } catch (Exception e) {
            %> Error: <%= e %><%
        }
    }
    if (files.length==0) {
    %>
      <i>No output.</i> </li>
    <%
    }
    
    fsClient.delete(outp, true);
    
} catch (Exception e) {
%>
    <b>Error connecting to Hadoop.</b><br/>
    ERROR: <%= e %><br/>
    <pre> <%= brooklyn.demo.webapp.hello.DemoUtils.getStackTrace(e) %></pre>
<%
} 
}
%>

    <br/>
    <p>Click <a href="hadoop-chat.jsp">here</a> to view the chat room.</p>
    <p>Click <a href="index.html">here</a> to go back to the main page.</p>

</body>
</html>
