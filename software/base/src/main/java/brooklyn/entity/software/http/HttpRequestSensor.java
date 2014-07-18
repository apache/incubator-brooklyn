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
package brooklyn.entity.software.http;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.AddSensor;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.time.Duration;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import java.util.Map;

/**
 * Configurable {@link brooklyn.entity.proxying.EntityInitializer} which adds an HTTP sensor feed to retrieve the
 * <code>JSONObject</code> from a JSON response in order to populate the sensor with the indicated <code>name</code>.
 */
@Beta
public final class HttpRequestSensor<T> extends AddSensor<T, AttributeSensor<T>> {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(HttpRequestSensor.class);

    public static final ConfigKey<String> JSON_PATH = ConfigKeys.newStringConfigKey("jsonPath");
    public static final ConfigKey<String> SENSOR_URI = ConfigKeys.newStringConfigKey("uri");

    private final String jsonPath;
    private final String uri;

    public HttpRequestSensor(Map<String, String> params) {
        this(ConfigBag.newInstance(params));
    }

    public HttpRequestSensor(ConfigBag params) {
        super(AddSensor.<T>newSensor(params));
        jsonPath = Preconditions.checkNotNull(params.get(JSON_PATH), JSON_PATH);
        uri = Preconditions.checkNotNull(params.get(SENSOR_URI), SENSOR_URI);
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        Duration period = entity.getConfig(SENSOR_PERIOD);
        if (period==null) period = Duration.ONE_SECOND;

        log.debug("Adding sensor "+sensor+" to "+entity+" polling "+uri+" for "+jsonPath);
        
        HttpPollConfig<T> pollConfig = new HttpPollConfig<T>(sensor)
                .checkSuccess(HttpValueFunctions.responseCodeEquals(200))
                .onFailureOrException(Functions.constant((T) null))
                .onSuccess(HttpValueFunctions.<T>jsonContentsFromPath(jsonPath))
                .period(period);

        HttpFeed.builder().entity(entity)
                .baseUri(uri)
                .poll(pollConfig)
                .build();
    }
}
