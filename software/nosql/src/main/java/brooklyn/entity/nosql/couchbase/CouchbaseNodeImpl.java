package brooklyn.entity.nosql.couchbase;

import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class CouchbaseNodeImpl extends SoftwareProcessImpl implements CouchbaseNode {
    
    HttpFeed httpFeed;

    @Override
    public Class<CouchbaseNodeDriver> getDriverInterface() {
        return CouchbaseNodeDriver.class;
    }

    @Override
    public CouchbaseNodeDriver getDriver() {
        return (CouchbaseNodeDriver) super.getDriver();
    }

    @Override
    public void init() {
        super.init();
        
        subscribe(this, Attributes.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> booleanSensorEvent) {
                if (Boolean.TRUE.equals(booleanSensorEvent.getValue())) {
                    String hostname = getAttribute(HOSTNAME);
                    String webPort = getConfig(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT).iterator().next().toString();
                    setAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_URL, format("http://%s:%s", hostname, webPort));
                }
            }
        });
    }

    protected Map<String, Object> obtainProvisioningFlags(@SuppressWarnings("rawtypes") MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(super.obtainProvisioningFlags(location));
        result.configure(CloudLocationConfig.OS_64_BIT, true);
        return result.getAllConfig();
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO this creates a huge list of inbound ports; much better to define on a security group using range syntax!
        int erlangRangeStart = getConfig(NODE_DATA_EXCHANGE_PORT_RANGE_START).iterator().next();
        int erlangRangeEnd = getConfig(NODE_DATA_EXCHANGE_PORT_RANGE_END).iterator().next();

        Set<Integer> newPorts = MutableSet.<Integer>copyOf(super.getRequiredOpenPorts());
        newPorts.remove(erlangRangeStart);
        newPorts.remove(erlangRangeEnd);
        for (int i = erlangRangeStart; i <= erlangRangeEnd; i++)
            newPorts.add(i);
        return newPorts;
    }

    @Override
    public void serverAdd(String serverToAdd, String username, String password) {
        getDriver().serverAdd(serverToAdd, username, password);
    }

    @Override
    public void rebalance() {
        getDriver().rebalance();
    }


    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
        
        Function<HttpToolResponse, JsonElement> getThisNodesStats = HttpValueFunctions.chain(
            HttpValueFunctions.jsonContents(), 
            JsonFunctions.walk("nodes"), 
            new Function<JsonElement, JsonElement>() {
                @Override public JsonElement apply(JsonElement input) {
                    JsonArray nodes = input.getAsJsonArray();
                    for (JsonElement element : nodes) {
                        if (Boolean.TRUE.equals(element.getAsJsonObject().get("thisNode").getAsBoolean())) {
                            return element.getAsJsonObject().get("interestingStats");
                        }
                    }
                    return null;
            }}
        );
        
        Integer rawPort = getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT);
        Preconditions.checkNotNull(rawPort, "HTTP_PORT sensors not set for %s; is an acceptable port available?", this);
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, rawPort);
        
        String adminUrl = String.format("http://%s:%s", hp.getHostText(), hp.getPort());
        
        httpFeed = HttpFeed.builder()
            .entity(this)
            .period(1000)
            .baseUri(adminUrl + "/pools/nodes/")
            .credentialsIfNotNull(getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME), getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.OPS)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("ops"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.COUCH_DOCS_DATA_SIZE)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("couch_docs_data_size"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.COUCH_DOCS_ACTUAL_DISK_SIZE)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("couch_docs_actual_disk_size"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.EP_BG_FETCHED)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("ep_bg_fetched"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.MEM_USED)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("mem_used"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.COUCH_VIEWS_ACTUAL_DISK_SIZE)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("couch_views_actual_disk_size"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.CURR_ITEMS)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("curr_items"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.VB_REPLICA_CURR_ITEMS)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("vb_replica_curr_items"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.COUCH_VIEWS_DATA_SIZE)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("couch_views_data_size"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.GET_HITS)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("get_hits"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.CMD_GET)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("cmd_get"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .poll(new HttpPollConfig<Integer>(CouchbaseNode.CURR_ITEMS_TOT)
                    .onSuccess(HttpValueFunctions.chain(getThisNodesStats, JsonFunctions.walk("curr_items_tot"), JsonFunctions.cast(Integer.class)))
                    .onFailureOrException(Functions.<Integer>constant(null)))
            .build();
    }

    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }


}
