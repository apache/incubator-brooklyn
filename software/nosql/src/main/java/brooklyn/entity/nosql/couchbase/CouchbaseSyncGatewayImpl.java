package brooklyn.entity.nosql.couchbase;


import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.net.HostAndPort;

public class CouchbaseSyncGatewayImpl extends SoftwareProcessImpl implements CouchbaseSyncGateway {

    private HttpFeed httpFeed;

    @Override
    public Class<CouchbaseSyncGatewayDriver> getDriverInterface() {
        return CouchbaseSyncGatewayDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    protected void connectServiceUpIsRunning() {


        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this,
                getAttribute(CouchbaseSyncGateway.ADMIN_REST_API_PORT));

        String managementUri = String.format("http://%s:%s",
                hp.getHostText(), hp.getPort());

        setAttribute(MANAGEMENT_URL, managementUri);

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(managementUri)
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200)))
                .build();

    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    protected void disconnectServiceUpIsRunning() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }
    
    static {
        RendererHints.register(MANAGEMENT_URL, new RendererHints.NamedActionWithUrl("Open"));
    }
}