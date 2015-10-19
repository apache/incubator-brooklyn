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
package org.apache.brooklyn.core.initializer;

import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.effector.MigrateEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public class AddMigrateEffectorEntityInitializer implements EntityInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(AddMigrateEffectorEntityInitializer.class);

    @Override
    public void apply(final EntityLocal entity) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding Migrate effector to {}", entity);
        }

        ((EntityInternal) entity).getMutableEntityType().addEffector(MigrateEffector.MIGRATE, new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                migrate((EntityInternal) entity, (String) parameters.getStringKey(MigrateEffector.MIGRATE_LOCATION_SPEC));
                return null;
            }
        });
    }

    private static void migrate(EntityInternal entity, String locationSpec) {
        if (entity.sensors().get(Attributes.SERVICE_STATE_ACTUAL) != Lifecycle.RUNNING) {
            // Is it necessary to check if the whole application is healthy?
            throw new RuntimeException("The entity needs to be healthy before the migration starts");
        }

        if (entity.getParent() != null && !entity.getParent().equals(entity.getApplication())) {
            /*
             * TODO: Allow nested entites to be migrated
             * If the entity has a parent different to the application root the migration cannot be done right now,
             * as it could lead into problems to deal with hierarchies like SameServerEntity -> Entity
             */

            throw new RuntimeException("Nested entities cannot be migrated right now");
        }

        // Retrieving the location from the catalog.
        Location newLocation = Entities.getManagementContext(entity).getLocationRegistry().resolve(locationSpec);

        // TODO: Find a better way to check if you're migrating an entity to the exactly same VM. This not always works.
        for (Location oldLocation : entity.getLocations()) {
            if (oldLocation.containsLocation(newLocation)) {
                LOG.warn("You cannot migrate an entity to the same location, the migration process will stop right now");
                return;
            }
        }

        LOG.info("Migration process of " + entity.getId() + " started.");

        // When we have the new location, we free the resources of the current instance
        entity.invoke(Startable.STOP, MutableMap.<String, Object>of()).blockUntilEnded();

        // Clearing old locations to remove the relationship with the previous instance
        entity.clearLocations();
        entity.addLocations(Lists.newArrayList(newLocation));

        // Starting the new instance
        entity.invoke(Startable.START, MutableMap.<String, Object>of()).blockUntilEnded();

        // Refresh all the dependent entities

        for (Entity applicationChild : entity.getApplication().getChildren()) {
            // TODO: Find a better way to refresh the application configuration.
            // TODO: Refresh nested entities or find a way to propagate the restart properly.

            // Restart any entity but the migrated one.
            if (applicationChild instanceof Startable) {
                if (entity.equals(applicationChild)) {
                    // The entity is sensors should rewired automatically on stop() + restart()
                } else {
                    // Restart the entity to fetch again all the dependencies (ie. attributeWhenReady ones)
                    ((Startable) applicationChild).restart();
                }
            }
        }

        LOG.info("Migration process of " + entity.getId() + " finished.");
    }
}
