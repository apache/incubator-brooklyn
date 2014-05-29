package brooklyn.entity.nosql.elasticsearch;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonElement;

public class ElasticSearchNodeImpl extends SoftwareProcessImpl implements ElasticSearchNode {
    
    HttpFeed httpFeed;
    
    public ElasticSearchNodeImpl() {
        
    }

    @Override
    public Class<ElasticSearchNodeDriver> getDriverInterface() {
        return ElasticSearchNodeDriver.class;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        Integer rawPort = getAttribute(HTTP_PORT);
        checkNotNull(rawPort, "HTTP_PORT sensors not set for %s; is an acceptable port available?", this);
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, rawPort);
        Function<JsonElement, String> getNodeId = new Function<JsonElement, String>() {
            @Override public String apply(JsonElement input) {
                return input.getAsJsonObject().entrySet().iterator().next().getKey();
            }
        };
        Function<JsonElement, JsonElement> getFirstNode = new Function<JsonElement, JsonElement>() {
            @Override public JsonElement apply(JsonElement input) {
                return input.getAsJsonObject().entrySet().iterator().next().getValue();
            }
        };
        httpFeed = HttpFeed.builder()
            .entity(this)
            .period(1000)
            .baseUri(String.format("http://%s:%s/_nodes/_local", hp.getHostText(), hp.getPort()))
            .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                .onFailureOrException(Functions.constant(false)))
                .poll(new HttpPollConfig<String>(NODE_ID)
                        .onSuccess(HttpValueFunctions.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("nodes"), getNodeId))
                        .onFailureOrException(Functions.constant("")))
            .poll(new HttpPollConfig<String>(NODE_NAME)
                .onSuccess(HttpValueFunctions.chain(HttpValueFunctions.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("nodes"), getFirstNode), 
                        JsonFunctions.walk("name"), JsonFunctions.cast(String.class)))
                .onFailureOrException(Functions.constant("")))
            .poll(new HttpPollConfig<String>(CLUSTER_NAME)
                .onSuccess(HttpValueFunctions.chain(HttpValueFunctions.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("nodes"), getFirstNode), 
                        JsonFunctions.walk("settings", "cluster", "name"), JsonFunctions.cast(String.class)))
                .onFailureOrException(Functions.constant("")))
            .build();
    }
    
    @Override
    protected void disconnectSensors() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }
}
