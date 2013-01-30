// TODO Untested code; see brooklyn-example for better maintained examples!
public class WebClusterDatabaseExample extends ApplicationBuilder {
    
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    @Override
    protected void doBuild() {
        MySqlNode mysql = createChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));
        
        ControlledDynamicWebAppCluster web = createChild(BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                .configure("memberSpec", BasicEntitySpec.newInstance(JBoss7Server.class)
                        .configure("httpPort", "8080+")
                        .configure("war", WAR_PATH)
                        .configure(javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))));
        
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                        metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                        sizeRange(1, 5).
                        metricRange(10, 100).
                        build());
    }
}
