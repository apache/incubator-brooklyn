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
package brooklyn.entity.brooklynnode.effector;

import java.util.Collection;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.EntityHttpClient;
import brooklyn.entity.brooklynnode.effector.CallbackEntityHttpClient.Request;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;

import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

public class TestHttpEntity extends AbstractEntity implements BrooklynNode {
    @SuppressWarnings("serial")
    public static final ConfigKey<Function<Request, String>> HTTP_CLIENT_CALLBACK = ConfigKeys.newConfigKey(new TypeToken<Function<Request, String>>(){}, "httpClientCallback");
    public static final AttributeSensor<Integer> HA_PRIORITY = new BasicAttributeSensor<Integer>(Integer.class, "priority");
    
    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(SetHAPriorityEffectorBody.SET_HA_PRIORITY);
        getMutableEntityType().addEffector(SetHAModeEffectorBody.SET_HA_MODE);
        setAttribute(HA_PRIORITY, 0);
    }

    @Override
    public EntityHttpClient http() {
        return new CallbackEntityHttpClient(this, getConfig(HTTP_CLIENT_CALLBACK));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void restart() {
    }

}
