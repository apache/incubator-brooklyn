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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;

import com.google.common.base.Function;

/**
 * A delegate entity for use as a {@link Group} child proxy for members.
 */
@ImplementedBy(DelegateEntityImpl.class)
public interface DelegateEntity extends Entity {

    AttributeSensorAndConfigKey<Entity, Entity> DELEGATE_ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class, "delegate.entity", "The delegate entity");

    AttributeSensor<String> DELEGATE_ENTITY_LINK = Sensors.newStringSensor("webapp.url", "The delegate entity link");

    /** Hints for rendering the delegate entity as a link in the Brooklyn console UI. */
    public static class EntityUrl {

        private static final AtomicBoolean initialized = new AtomicBoolean(false);
        private static final Function<Entity, String> entityUrlFunction = new Function<Entity, String>() {
            @Override
            public String apply(Entity input) {
                if (input==null) return null;
                Entity entity = (Entity) input;
                String url = String.format("#/v1/applications/%s/entities/%s", entity.getApplicationId(), entity.getId());
                return url;
            }
        };

        public static Function<Entity, String> entityUrl() { return entityUrlFunction; }

        /** Setup renderer hints. */
        public static void init() {
            if (initialized.getAndSet(true)) return;

            RendererHints.register(DELEGATE_ENTITY, RendererHints.namedActionWithUrl(entityUrl()));
            RendererHints.register(DELEGATE_ENTITY_LINK, RendererHints.namedActionWithUrl());
        }

        static {
            init();
        }
    }

}
