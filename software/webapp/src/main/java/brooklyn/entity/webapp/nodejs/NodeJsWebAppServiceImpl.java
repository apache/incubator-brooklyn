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
package brooklyn.entity.webapp.nodejs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.base.Predicates;
import com.google.common.net.HostAndPort;

public class NodeJsWebAppServiceImpl extends SoftwareProcessImpl implements NodeJsWebAppService {

    private static final Logger LOG = LoggerFactory.getLogger(NodeJsWebAppService.class);

    private transient HttpFeed httpFeed;

    @Override
    public Class<?> getDriverInterface() {
        return NodeJsWebAppDriver.class;
    }

    @Override
    public NodeJsWebAppDriver getDriver() {
        return (NodeJsWebAppDriver) super.getDriver();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        ConfigToAttributes.apply(this);

        HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, getHttpPort());
        String nodeJsUrl = String.format("http://%s:%d", accessible.getHostText(), accessible.getPort());
        LOG.info("Connecting to {}", nodeJsUrl);

        httpFeed = HttpFeed.builder()
                .entity(this)
                .baseUri(nodeJsUrl)
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .suburl(getConfig(NodeJsWebAppService.SERVICE_UP_PATH))
                        .checkSuccess(Predicates.alwaysTrue())
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .setOnException(false))
                .build();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    @Override
    public void disconnectSensors() {
        if (httpFeed != null) httpFeed.stop();
        super.disconnectSensors();
    }

    @Override
    protected void doStop() {
        super.doStop();

        setAttribute(REQUESTS_PER_SECOND_LAST, 0D);
        setAttribute(REQUESTS_PER_SECOND_IN_WINDOW, 0D);
    }

    @Override
    public Integer getHttpPort() { return getAttribute(Attributes.HTTP_PORT); }

}
