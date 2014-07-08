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
package brooklyn.entity.basic;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.config.ConfigBag;

import com.google.common.reflect.TypeToken;

/** implementation of {@link Startable} which calls to tasks registered against effectors
 * if the methods are invoked directly; note this will loop indefinetly if no method impl
 * is supplied and no task is specified on an (overriding) effector
 * <p>
 * TODO we should have a better way to autostart, basically checking whether there is
 * a start effector, i.e. not requiring a start method in Startable
 * (and same for stop and restart) */
public class EffectorStartableImpl extends AbstractEntity implements BasicStartable {

    private static final long serialVersionUID = -7109357808001370568L;
    
    private static final Logger log = LoggerFactory.getLogger(EffectorStartableImpl.class);

    public static class StartParameters { 
        // TODO polymorphic parametrisation of effetor, take LOCATION, take strings, etc
        @SuppressWarnings("serial")
        public static final ConfigKey<Collection<? extends Location>> LOCATIONS =
            ConfigKeys.newConfigKey(new TypeToken<Collection<? extends Location>>() {}, "locations", 
                "locations where the entity should be started");
    }

    @Override
    @Effector(description = "Start the process/service represented by an entity")
    public void start(@EffectorParam(name = "locations") Collection<? extends Location> locations) {
        log.info("Invoking start (method) on "+this);
        invoke(START, ConfigBag.newInstance().configure(StartParameters.LOCATIONS, locations).getAllConfig())
            .getUnchecked();
    }

    @Override
    @Effector(description = "Stop the process/service represented by an entity")
    public void stop() {
        log.info("Invoking stop (method) on "+this);
        invoke(STOP).getUnchecked();
    }

    @Override
    @Effector(description = "Restart the process/service represented by an entity")
    public void restart() {
        log.info("Invoking restart (method) on "+this);
        invoke(RESTART).getUnchecked();
    }

}
