// TODO Untested code; see brooklyn-example for better maintained examples!
public class TomcatClusterWithNginxApp extends ApplicationBuilder {
    @Override
    protected void doBuild() {
        createChild(EntitySpecs.spec(NginxController.class)
                .configure("domain", "brooklyn.geopaas.org")
                .configure("port", "8000+")
                .configure("portNumberSensor", Attributes.HTTP_PORT));
        
        createChild(EntitySpecs.spec(ControlledDynamicWebAppCluster.class)
                .configure("controller", nginxController)
                .configure("memberSpec", : EntitySpecs.spec(TomcatServer.class)
                        .configure("httpPort", "8080+")
                        .configure("war", "/path/to/booking-mvc.war"))
                .configure("initialSize", 2));
    }
}
