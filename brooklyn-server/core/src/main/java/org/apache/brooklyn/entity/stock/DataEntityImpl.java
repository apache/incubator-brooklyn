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
package org.apache.brooklyn.entity.stock;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;

import com.google.common.base.Functions;
import com.google.common.base.Supplier;

public class DataEntityImpl extends AbstractEntity implements DataEntity {

    private FunctionFeed feed;

    public DataEntityImpl() { }

    @Override
    public void start(Collection<? extends Location> locations) {
        addLocations(locations);
        connectSensors();
        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        sensors().set(SERVICE_UP, Boolean.FALSE);
        disconnectSensors();
    }

    @Override
    public void restart() {
        stop();
        start(getLocations());
    }

    protected void connectSensors() {
        FunctionFeed.Builder builder = FunctionFeed.builder()
                .entity(this)
                .period(getConfig(POLL_PERIOD));

        Map<AttributeSensor<?>, Supplier<?>> map = getConfig(SENSOR_SUPPLIER_MAP);
        if (map != null && map.size() > 0) {
            for (Map.Entry<AttributeSensor<?>, Supplier<?>> entry : map.entrySet()) {
                final AttributeSensor sensor = entry.getKey();
                final Supplier<?> supplier = entry.getValue();
                builder.poll(new FunctionPollConfig<Object, Object>(sensor)
                        .supplier(supplier)
                        .onFailureOrException(Functions.constant(null)));
            }
        }

        feed = builder.build();
    }

    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }
}
