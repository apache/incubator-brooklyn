// TODO Untested code; see brooklyn-example for better maintained examples!
public class TomcatFabricApp extends ApplicationBuilder {
    @Override
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(DynamicFabric.class)
                .configure("displayName", "WebFabric")
                .configure("displayNamePrefix", "")
                .configure("displayNameSuffix", " web cluster")
                .configure("memberSpec", BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                        .configure("initialSize", 2)
                        .configure("memberSpec", : BasicEntitySpec.newInstance(TomcatServer.class)
                                .configure("httpPort", "8080+")
                                .configure("war", "/path/to/booking-mvc.war"))));
    }
}
