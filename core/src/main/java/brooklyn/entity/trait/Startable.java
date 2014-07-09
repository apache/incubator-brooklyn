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
package brooklyn.entity.trait;

import java.util.Collection;
import java.util.Collections;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

/**
 * This interface describes an {@link brooklyn.entity.Entity} that can be started and stopped.
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
    };
    
    brooklyn.entity.Effector<Void> START = Effectors.effector(new MethodEffector<Void>(Startable.class, "start"))
        // override start to take strings etc
        .parameter(StartEffectorBody.LOCATIONS)
        .impl(new StartEffectorBody())
        .build();
    brooklyn.entity.Effector<Void> STOP = new MethodEffector<Void>(Startable.class, "stop");
    brooklyn.entity.Effector<Void> RESTART = new MethodEffector<Void>(Startable.class,"restart");

    /**
     * Start the entity in the given collection of locations.
     */
    @brooklyn.entity.annotation.Effector(description="Start the process/service represented by an entity")
    void start(@EffectorParam(name="locations") Collection<? extends Location> locations);

    /**
     * Stop the entity.
     */
    @brooklyn.entity.annotation.Effector(description="Stop the process/service represented by an entity")
    void stop();

    /**
     * Restart the entity.
     */
    @brooklyn.entity.annotation.Effector(description="Restart the process/service represented by an entity")
    void restart();
}
