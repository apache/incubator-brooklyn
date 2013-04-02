// TODO Untested code; see brooklyn-example for better maintained examples!
public class TomcatServerApp extends AbstractApplication {
    @Override
    public void init() {
        addChild(EntitySpecs.spec(TomcatServer.class)
                .configure("httpPort", "8080+")
                .configure("war", "/path/to/booking-mvc.war")));
    }
}
