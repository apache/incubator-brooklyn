// TODO Untested code; see brooklyn-example for better maintained examples!
public class TomcatClusterWithNginxApp extends AbstractApplication {
    @Override
    public void init() {
        addChild(EntitySpec.create(NginxController.class)
                .configure("domain", "brooklyn.geopaas.org")
                .configure("port", "8000+")
                .configure("portNumberSensor", Attributes.HTTP_PORT));
        
        addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("controller", nginxController)
                .configure("memberSpec", : EntitySpec.create(TomcatServer.class)
                        .configure("httpPort", "8080+")
                        .configure("war", "/path/to/booking-mvc.war"))
                .configure("initialSize", 2));
    }
}
