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
package brooklyn.entity.brooklynnode;

import java.net.URI;
import java.util.Collection;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.brooklynnode.BrooklynNodeImpl.DeployBlueprintEffectorBody;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;

/** Implementation of BrooklynNode which just presents the node where this is running, for convenience;
 * 
 *  start/stop/restart have no effect; 
 *  sensors are connected;
 *  deploy blueprint assumes that a REST endpoint is available */
public class SameBrooklynNodeImpl extends AbstractEntity implements BrooklynNode {
    
    private HttpFeed httpFeed;

    @Override
    public void start(Collection<? extends Location> locations) {
        connectSensors();
    }

    @Override
    public void stop() {
        disconnectSensors();
    }

    @Override
    public void restart() {
        return;
    }

    
    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(DeployBlueprintEffectorBody.DEPLOY_BLUEPRINT);
    }
    
    protected void connectSensors() {
        URI webConsoleUri = getManagementContext().getManagementNodeUri().orNull();
        setAttribute(WEB_CONSOLE_URI, webConsoleUri);

        if (webConsoleUri != null) {
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri(webConsoleUri)
                    .credentialsIfNotNull(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                            .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                            .setOnFailureOrException(false))
                    .build();

        } else {
            setAttribute(SERVICE_UP, true);
        }
    }
    
    protected void disconnectSensors() {
        if (httpFeed != null) httpFeed.stop();
    }

}
