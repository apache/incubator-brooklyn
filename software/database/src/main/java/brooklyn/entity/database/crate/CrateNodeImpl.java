package brooklyn.entity.database.crate;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.jmx.JmxFeed;


public class CrateNodeImpl extends SoftwareProcessImpl implements CrateNode{

    private JmxFeed jmxFeed;
    private HttpFeed httpFeed;

    private static final int CRATE_PORT = 4200;

    static {
        JavaAppUtils.init();
    }

    @Override
    public Class getDriverInterface() {
        return CrateNodeDriver.class;
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        jmxFeed.stop();
        httpFeed.stop();
        super.disconnectSensors();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
        jmxFeed = getJmxFeed();
        String uri = "http://" + getAttribute(HOSTNAME) + ":" + CRATE_PORT;
        setAttribute(MANAGEMENT_URI, uri);

        httpFeed = HttpFeed.builder()
                .entity(this)
                .baseUri(uri)
                .poll(new HttpPollConfig<String>(CrateNode.SERVER_NAME)
                        .onSuccess(HttpValueFunctions.jsonContents("name", String.class)))
                .poll(new HttpPollConfig<Integer>(CrateNode.SERVER_STATUS)
                        .onSuccess(HttpValueFunctions.jsonContents("status", Integer.class)))
                .poll(new HttpPollConfig<String>(CrateNode.SERVER_BUILD_TIMESTAMP)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] {"version", "build_timestamp"}, String.class)))
                .build();
    }

    @Override
    protected void postStart() {
        super.postStart();

    }
    private JmxFeed getJmxFeed() {
        return JavaAppUtils.connectMXBeanSensors(this);
    }
}
