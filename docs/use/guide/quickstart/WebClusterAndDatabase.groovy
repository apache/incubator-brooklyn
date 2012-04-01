public class WebClusterDatabaseExample extends AbstractApplication {
    ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(this,
        war: "classpath://hello-world-webapp.war");

    MySqlNode mysql = new MySqlNode(this,
        creationScriptUrl: "classpath://visitors-database-setup.sql");
    
    {
        web.factory.configure(
            httpPort: "8080+",
            (JBoss7Server.JAVA_OPTIONS):
                // -Dbrooklyn.example.db.url="jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn\\&password=br00k11n"
                ["brooklyn.example.db.url": valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL,
                    { "jdbc:"+it+"visitors?user=brooklyn\\&password=br00k11n" }) ]);
                    
        web.cluster.addPolicy(
            new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                setSizeRange(1, 5).
                setMetricRange(10, 100);
    }
}
