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
package brooklyn.entity.nosql.elasticsearch;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.util.guava.Functionals;
import brooklyn.util.guava.Maybe;
import brooklyn.util.guava.MaybeFunctions;
import brooklyn.util.guava.TypeTokens;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonElement;

public class ElasticSearchNodeImpl extends SoftwareProcessImpl implements ElasticSearchNode {
    
    protected static final Function<Maybe<JsonElement>, Maybe<JsonElement>> GET_FIRST_NODE_FROM_NODES = new Function<Maybe<JsonElement>, Maybe<JsonElement>>() {
        @Override public Maybe<JsonElement> apply(Maybe<JsonElement> input) {
            if (input.isAbsent()) {
                return input;
            }
            return Maybe.fromNullable(input.get().getAsJsonObject().entrySet().iterator().next().getValue());
        }
    };
    
    protected static final Function<HttpToolResponse, Maybe<JsonElement>> GET_FIRST_NODE = Functionals.chain(HttpValueFunctions.jsonContents(), 
            MaybeFunctions.<JsonElement>wrap(), JsonFunctions.walkM("nodes"), GET_FIRST_NODE_FROM_NODES);
    
    
    HttpFeed httpFeed;

    @Override
    public Class<ElasticSearchNodeDriver> getDriverInterface() {
        return ElasticSearchNodeDriver.class;
    }
    
    protected static final <T> HttpPollConfig<T> getSensorFromNodeStat(AttributeSensor<T> sensor, String... jsonPath) {
        return new HttpPollConfig<T>(sensor)
            .onSuccess(Functionals.chain(GET_FIRST_NODE, JsonFunctions.walkM(jsonPath), JsonFunctions.castM(TypeTokens.getRawRawType(sensor.getTypeToken()), null)))
            .onFailureOrException(Functions.<T>constant(null));
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        Integer rawPort = getAttribute(HTTP_PORT);
        checkNotNull(rawPort, "HTTP_PORT sensors not set for %s; is an acceptable port available?", this);
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, rawPort);
        Function<Maybe<JsonElement>, String> getNodeId = new Function<Maybe<JsonElement>, String>() {
            @Override public String apply(Maybe<JsonElement> input) {
                if (input.isAbsent()) {
                    return null;
                }
                return input.get().getAsJsonObject().entrySet().iterator().next().getKey();
            }
        };
        httpFeed = HttpFeed.builder()
            .entity(this)
            .period(1000)
            .baseUri(String.format("http://%s:%s/_nodes/_local/stats", hp.getHostText(), hp.getPort()))
            .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                .onFailureOrException(Functions.constant(false)))
            .poll(new HttpPollConfig<String>(NODE_ID)
                .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), MaybeFunctions.<JsonElement>wrap(), JsonFunctions.walkM("nodes"), getNodeId))
                .onFailureOrException(Functions.constant("")))
            .poll(getSensorFromNodeStat(NODE_NAME, "name"))
            .poll(getSensorFromNodeStat(DOCUMENT_COUNT, "indices", "docs", "count"))
            .poll(getSensorFromNodeStat(STORE_BYTES, "indices", "store", "size_in_bytes"))
            .poll(getSensorFromNodeStat(GET_TOTAL, "indices", "get", "total"))
            .poll(getSensorFromNodeStat(GET_TIME_IN_MILLIS, "indices", "get", "time_in_millis"))
            .poll(getSensorFromNodeStat(SEARCH_QUERY_TOTAL, "indices", "search", "query_total"))
            .poll(getSensorFromNodeStat(SEARCH_QUERY_TIME_IN_MILLIS, "indices", "search", "query_time_in_millis"))
            .poll(new HttpPollConfig<String>(CLUSTER_NAME)
                .onSuccess(HttpValueFunctions.jsonContents("cluster_name", String.class)))
            .build();
    }
    
    @Override
    protected void disconnectSensors() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }
}
