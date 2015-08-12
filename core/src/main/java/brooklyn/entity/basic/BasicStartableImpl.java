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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.management.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class BasicStartableImpl extends AbstractEntity implements BasicStartable {

    private static final Logger log = LoggerFactory.getLogger(BasicStartableImpl.class);

    @Override
    public void start(Collection<? extends Location> locations) {
        log.info("Starting entity "+this+" at "+locations);
        try {
            ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

            addLocations(locations);

            // essentially does StartableMethods.start(this, locations),
            // but optionally filters locations for each child

            brooklyn.location.basic.Locations.LocationsFilter filter = getConfig(LOCATIONS_FILTER);
            Iterable<Entity> startables = filterStartableManagedEntities(getChildren());
            if (!Iterables.isEmpty(startables)) {
                List<Task<?>> tasks = Lists.newArrayListWithCapacity(Iterables.size(startables));
                for (final Entity entity : startables) {
                    Collection<? extends Location> l2 = locations;
                    if (filter != null) {
                        l2 = filter.filterForContext(new ArrayList<Location>(locations), entity);
                        log.debug("Child " + entity + " of " + this + " being started in filtered location list: " + l2);
                    }
                    tasks.add(Entities.invokeEffectorWithArgs(this, entity, Startable.START, l2));
                }
                for (Task<?> t : tasks) {
                    t.getUnchecked();
                }
            }
            setAttribute(Attributes.SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    @Override
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        setAttribute(SERVICE_UP, false);
        try {
            StartableMethods.stop(this);
            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void restart() {
        StartableMethods.restart(this);
    }

    // TODO make public in StartableMethods
    private static Iterable<Entity> filterStartableManagedEntities(Iterable<Entity> contenders) {
        return Iterables.filter(contenders, Predicates.and(Predicates.instanceOf(Startable.class), EntityPredicates.isManaged()));
    }
}
