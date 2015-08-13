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

import java.net.URI;

import net.minidev.json.JSONObject;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.effector.AddSensor;
import brooklyn.entity.software.java.JmxAttributeSensor;
import brooklyn.entity.software.ssh.SshCommandSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.config.ConfigBag;

import com.google.common.annotations.Beta;
import com.google.common.base.Functions;
import com.google.common.base.Supplier;

/**
 * Configurable {@link org.apache.brooklyn.api.entity.proxying.EntityInitializer} which adds an HTTP sensor feed to retrieve the
 * {@link JSONObject} from a JSON response in order to populate the sensor with the data at the {@code jsonPath}.
 *
 * @see SshCommandSensor
 * @see JmxAttributeSensor
 */
@Beta
public final class HttpRequestSensor<T> extends AddSensor<T> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestSensor.class);

    public static final ConfigKey<String> SENSOR_URI = ConfigKeys.newStringConfigKey("uri", "HTTP URI to poll for JSON");
    public static final ConfigKey<String> JSON_PATH = ConfigKeys.newStringConfigKey("jsonPath", "JSON path to select in HTTP response; default $", "$");
    public static final ConfigKey<String> USERNAME = ConfigKeys.newStringConfigKey("username", "Username for HTTP request, if required");
    public static final ConfigKey<String> PASSWORD = ConfigKeys.newStringConfigKey("password", "Password for HTTP request, if required");

    protected final Supplier<URI> uri;
    protected final String jsonPath;
    protected final String username;
    protected final String password;

    public HttpRequestSensor(final ConfigBag params) {
        super(params);

        uri = new Supplier<URI>() {
            @Override
            public URI get() {
                return URI.create(params.get(SENSOR_URI));
            }
        };
        jsonPath = params.get(JSON_PATH);
        username = params.get(USERNAME);
        password = params.get(PASSWORD);
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding HTTP JSON sensor {} to {}", name, entity);
        }

        HttpPollConfig<T> pollConfig = new HttpPollConfig<T>(sensor)
                .checkSuccess(HttpValueFunctions.responseCodeEquals(200))
                .onFailureOrException(Functions.constant((T) null))
                .onSuccess(HttpValueFunctions.<T>jsonContentsFromPath(jsonPath))
                .period(period);

        HttpFeed.builder().entity(entity)
                .baseUri(uri)
                .credentialsIfNotNull(username, password)
                .poll(pollConfig)
                .build();
    }
}
