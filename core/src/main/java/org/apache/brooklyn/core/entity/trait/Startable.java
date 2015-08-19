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
package org.apache.brooklyn.core.entity.trait;

import java.util.Collection;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.effector.core.EffectorBody;
import org.apache.brooklyn.effector.core.Effectors;
import org.apache.brooklyn.effector.core.MethodEffector;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interface describes an {@link org.apache.brooklyn.api.entity.Entity} that can be started and stopped.
 *
 * The {@link Effector}s are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {

    
    AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;

    public static class StartEffectorBody extends EffectorBody<Void> {
        public static final ConfigKey<Object> LOCATIONS = ConfigKeys.newConfigKey(Object.class, "locations",
            "The location or locations to start in, as a string, a location object, a list of strings, "
            + "or a list of location objects");
        @Override public Void call(ConfigBag parameters) {
            parameters.put(LOCATIONS, entity().getManagementContext().getLocationRegistry().resolveList(parameters.get(LOCATIONS)));
            return new MethodEffector<Void>(Startable.class, "start").call(entity(), parameters.getAllConfig());
        }
    }

    public static class StopEffectorBody extends EffectorBody<Void> {
        private static final Logger log = LoggerFactory.getLogger(Startable.class);
        
        @Override public Void call(ConfigBag parameters) {
            if (!parameters.isEmpty()) {
                log.warn("Parameters "+parameters+" not supported for call to "+entity()+" - "+Tasks.current());
            }
            
            return new MethodEffector<Void>(Startable.class, "stop").call(entity(), parameters.getAllConfig());
        }
    }

    public static class RestartEffectorBody extends EffectorBody<Void> {
        private static final Logger log = LoggerFactory.getLogger(Startable.class);

        @Override public Void call(ConfigBag parameters) {
            if (!parameters.isEmpty()) {
                log.warn("Parameters "+parameters+" not supported for call to "+entity()+" - "+Tasks.current());
            }
            return new MethodEffector<Void>(Startable.class, "restart").call(entity(), parameters.getAllConfig());
        }
    }

    org.apache.brooklyn.api.effector.Effector<Void> START = Effectors.effector(new MethodEffector<Void>(Startable.class, "start"))
        // override start to take strings etc
        .parameter(StartEffectorBody.LOCATIONS)
        .impl(new StartEffectorBody())
        .build();
    
    org.apache.brooklyn.api.effector.Effector<Void> STOP = Effectors.effector(new MethodEffector<Void>(Startable.class, "stop"))
        .impl(new StopEffectorBody())
        .build();
    
    org.apache.brooklyn.api.effector.Effector<Void> RESTART = Effectors.effector(new MethodEffector<Void>(Startable.class, "restart"))
        .impl(new RestartEffectorBody())
        .build();

    /**
     * Start the entity in the given collection of locations.
     * <p>
     * Some entities may define custom {@link Effector} implementations which support
     * a richer set of parameters.  See the entity-specific {@link #START} effector declaration.
     */
    @org.apache.brooklyn.core.annotation.Effector(description="Start the process/service represented by an entity")
    void start(@EffectorParam(name="locations") Collection<? extends Location> locations);

    /**
     * Stop the entity.
     * <p>
     * Some entities may define custom {@link Effector} implementations which support
     * a richer set of parameters.  See the entity-specific {@link #STOP} effector declaration.
     */
    @org.apache.brooklyn.core.annotation.Effector(description="Stop the process/service represented by an entity")
    void stop();

    /**
     * Restart the entity.
     * <p>
     * Some entities may define custom {@link Effector} implementations which support
     * a richer set of parameters.  See the entity-specific {@link #RESTART} effector declaration.
     */
    @org.apache.brooklyn.core.annotation.Effector(description="Restart the process/service represented by an entity")
    void restart();
}
