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

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;

/**
 * A registry of driver classes, keyed off the driver-interface + location type it is for.
 * 
 * @author Aled Sage
 */
public class RegistryEntityDriverFactory implements EntityDriverManager {

    private final Map<DriverLocationTuple, Class<? extends EntityDriver>> registry = new LinkedHashMap<DriverLocationTuple, Class<? extends EntityDriver>>();

    @Override
    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location) {
        Class<? extends D> driverClass = lookupDriver(entity.getDriverInterface(), location);
        return newDriverInstance(driverClass, entity, location);
    }

    public boolean hasDriver(DriverDependentEntity<?> entity, Location location) {
        return lookupDriver(entity.getDriverInterface(), location) != null;
    }

    public <D extends EntityDriver> void registerDriver(Class<D> driverInterface, Class<? extends Location> locationClazz, Class<? extends D> driverClazz) {
        synchronized (registry) {
            registry.put(new DriverLocationTuple(driverInterface, locationClazz), driverClazz);
        }
    }

    @SuppressWarnings("unchecked")
    private <D extends EntityDriver> Class<? extends D> lookupDriver(Class<D> driverInterface, Location location) {
        synchronized (registry) {
            for (DriverLocationTuple contender : registry.keySet()) {
                if (contender.matches(driverInterface, location)) {
                    return (Class<? extends D>) registry.get(contender);
                }
            }
        }
        return null;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <D> Constructor<D> getConstructor(Class<? extends D> driverClass) {
        for (Constructor constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return constructor;
            }
        }

        //TODO:
        throw new RuntimeException(String.format("Class [%s] has no constructor with 2 arguments",driverClass.getName()));
    }

    private <D> D newDriverInstance(Class<D> driverClass, Entity entity, Location location) {
        Constructor<D> constructor = getConstructor(driverClass);
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(entity, location);
        } catch (InstantiationException e) {
            throw Throwables.propagate(e);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e);
        }
    }

    private static class DriverLocationTuple {
        private final Class<? extends EntityDriver> driverInterface;
        private final Class<? extends Location> locationClazz;
        
        public DriverLocationTuple(Class<? extends EntityDriver> driverInterface, Class<? extends Location> locationClazz) {
            this.driverInterface = checkNotNull(driverInterface, "driver interface");
            this.locationClazz = checkNotNull(locationClazz, "location class");
        }
        
        public boolean matches(Class<? extends EntityDriver> driver, Location location) {
            return driverInterface.isAssignableFrom(driver) && locationClazz.isInstance(location);
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(driverInterface, locationClazz);
        }
        
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof DriverLocationTuple)) {
                return false;
            }
            DriverLocationTuple o = (DriverLocationTuple) other;
            return driverInterface.equals(o.driverInterface) && locationClazz.equals(o.locationClazz);
        }
    }
}
