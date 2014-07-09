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

import brooklyn.entity.Entity;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigurableEntityFactoryFromEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {

   private final EntityFactory<? extends T> factory;

    public ConfigurableEntityFactoryFromEntityFactory(EntityFactory<? extends T> entityFactory){
        this(new HashMap(),entityFactory);
    }

    public ConfigurableEntityFactoryFromEntityFactory(Map flags, EntityFactory<? extends T> factory) {
        super(flags);
        this.factory = checkNotNull(factory, "factory");
    }

    @Override
    public T newEntity2(Map flags, Entity parent) {
        return factory.newEntity(flags, parent);
    }
}