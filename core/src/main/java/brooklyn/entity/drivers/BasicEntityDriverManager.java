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
package brooklyn.entity.drivers;

import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.entity.drivers.EntityDriver;
import org.apache.brooklyn.api.entity.drivers.EntityDriverManager;

import com.google.common.annotations.Beta;

import org.apache.brooklyn.location.Location;

public class BasicEntityDriverManager implements EntityDriverManager {

    private final RegistryEntityDriverFactory registry;
    private final ReflectiveEntityDriverFactory reflective;
    
    public BasicEntityDriverManager() {
        registry = new RegistryEntityDriverFactory();
        reflective = new ReflectiveEntityDriverFactory();
    }
    
    /** driver override mechanism; experimental @since 0.7.0 */
    @Beta
    public ReflectiveEntityDriverFactory getReflectiveDriverFactory() {
        return reflective;
    }
    
    public <D extends EntityDriver> void registerDriver(Class<D> driverInterface, Class<? extends Location> locationClazz, Class<? extends D> driverClazz) {
        registry.registerDriver(driverInterface, locationClazz, driverClazz);
    }
    
    @Override
    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        if (registry.hasDriver(entity, location)) {
            return registry.build(entity, location);
        } else {
            return reflective.build(entity, location);
        }
    }
}