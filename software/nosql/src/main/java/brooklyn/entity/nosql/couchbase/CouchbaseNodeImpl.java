/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.couchbase;

import static java.lang.String.format;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Functionals;
import brooklyn.util.guava.MaybeFunctions;
import brooklyn.util.guava.TypeTokens;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class CouchbaseNodeImpl extends SoftwareProcessImpl implements CouchbaseNode {
    
    private static final Logger log = LoggerFactory.getLogger(CouchbaseNodeImpl.class);
    
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
    public void serverAddAndRebalance(String serverToAdd, String username, String password) {
        getDriver().serverAddAndRebalance(serverToAdd, username, password);
    }

    @Override
    public void rebalance() {
        getDriver().rebalance();
    }

    protected final static Function<HttpToolResponse, JsonElement> GET_THIS_NODE_STATS = Functionals.chain(
        HttpValueFunctions.jsonContents(), 
        JsonFunctions.walk("nodes"), 
        new Function<JsonElement, JsonElement>() {
            @Override public JsonElement apply(JsonElement input) {
                JsonArray nodes = input.getAsJsonArray();
                for (JsonElement element : nodes) {
                    JsonElement thisNode = element.getAsJsonObject().get("thisNode");
                    if (thisNode!=null && Boolean.TRUE.equals(thisNode.getAsBoolean())) {
                        return element.getAsJsonObject().get("interestingStats");
                    }
                }
                return null;
        }}
    );
    
    protected final static <T> HttpPollConfig<T> getSensorFromNodeStat(AttributeSensor<T> sensor, String ...jsonPath) {
        return new HttpPollConfig<T>(sensor)
            .onSuccess(Functionals.chain(GET_THIS_NODE_STATS, 
                MaybeFunctions.<JsonElement>wrap(), 
                JsonFunctions.walkM(jsonPath), 
                JsonFunctions.castM(TypeTokens.getRawRawType(sensor.getTypeToken()), null)))
            .onFailureOrException(Functions.<T>constant(null));
    }

    @Override
    protected void postStart() {
        super.postStart();
        renameServerToPublicHostname();
    }
    
    protected void renameServerToPublicHostname() {
        // http://docs.couchbase.com/couchbase-manual-2.5/cb-install/#couchbase-getting-started-hostnames
        URI apiUri = null;
        try {
            String hostname = getAttribute(Attributes.HOSTNAME); 
            String port = ""+getAttribute(COUCHBASE_WEB_ADMIN_PORT);
            apiUri = new URI("http://"+hostname+":"+port+"/node/controller/rename");
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                getConfig(COUCHBASE_ADMIN_USERNAME), getConfig(COUCHBASE_ADMIN_PASSWORD));
            HttpToolResponse response = HttpTool.httpPost(HttpTool.httpClientBuilder()
                // the uri is required by the HttpClientBuilder in order to set the AuthScope of the credentials
                .uri(apiUri)
                .credentials(credentials)
                .build(), 
                apiUri, 
                MutableMap.of(
                    com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA.toString(),
                    com.google.common.net.HttpHeaders.ACCEPT, "*/*",
                    // this appears needed; without it we get org.apache.http.NoHttpResponseException !?
                    com.google.common.net.HttpHeaders.AUTHORIZATION, HttpTool.toBasicAuthorizationValue(credentials)),
                ("hostname="+Urls.encode(hostname)).getBytes());
            log.debug("Renamed Couchbase server "+this+" via "+apiUri+": "+response);
            if (!HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
                log.warn("Invalid response code, renaming "+apiUri+": "+response);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Error renaming server, using "+apiUri+": "+e, e);
        }
    }

    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
                
        Integer rawPort = getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT);
        Preconditions.checkNotNull(rawPort, "HTTP_PORT sensors not set for %s; is an acceptable port available?", this);
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, rawPort);
        
        String adminUrl = String.format("http://%s:%s", hp.getHostText(), hp.getPort());
        
        httpFeed = HttpFeed.builder()
            .entity(this)
            .period(1000)
            .baseUri(adminUrl + "/pools/nodes/")
            .credentialsIfNotNull(getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME), getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD))
            .poll(getSensorFromNodeStat(CouchbaseNode.OPS, "ops"))
            .poll(getSensorFromNodeStat(CouchbaseNode.COUCH_DOCS_DATA_SIZE, "couch_docs_data_size"))
            .poll(getSensorFromNodeStat(CouchbaseNode.COUCH_DOCS_ACTUAL_DISK_SIZE, "couch_docs_actual_disk_size"))
            .poll(getSensorFromNodeStat(CouchbaseNode.EP_BG_FETCHED, "ep_bg_fetched"))
            .poll(getSensorFromNodeStat(CouchbaseNode.MEM_USED, "mem_used"))
            .poll(getSensorFromNodeStat(CouchbaseNode.COUCH_VIEWS_ACTUAL_DISK_SIZE, "couch_views_actual_disk_size"))
            .poll(getSensorFromNodeStat(CouchbaseNode.CURR_ITEMS, "curr_items"))
            .poll(getSensorFromNodeStat(CouchbaseNode.VB_REPLICA_CURR_ITEMS, "vb_replica_curr_items"))
            .poll(getSensorFromNodeStat(CouchbaseNode.COUCH_VIEWS_DATA_SIZE, "couch_views_data_size"))
            .poll(getSensorFromNodeStat(CouchbaseNode.GET_HITS, "get_hits"))
            .poll(getSensorFromNodeStat(CouchbaseNode.CMD_GET, "cmd_get"))
            .poll(getSensorFromNodeStat(CouchbaseNode.CURR_ITEMS_TOT, "curr_items_tot"))
            .poll(new HttpPollConfig<String>(CouchbaseNode.REBALANCE_STATUS)
                        .onSuccess(HttpValueFunctions.jsonContents("rebalanceStatus", String.class))
                        .onFailureOrException(Functions.constant("Could not retrieve")))
            .build();
    }

    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }

    @Override
    public void bucketCreate(String bucketName, String bucketType, Integer bucketPort, Integer bucketRamSize, Integer bucketReplica) {
        getDriver().bucketCreate(bucketName, bucketType, bucketPort, bucketRamSize, bucketReplica);
    }
}
