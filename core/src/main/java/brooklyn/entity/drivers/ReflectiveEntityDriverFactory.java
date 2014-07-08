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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Throwables;

/**
 * Follows a class naming convention: the driver interface must end in "Driver", and the implementation 
 * must match the driver interface name but with a suffix like "SshDriver" instead of "Driver".
 * 
 * Reflectively instantiates and returns the driver, based on the location passed in.
 * 
 * @author Peter Veentjer
 */
public class ReflectiveEntityDriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectiveEntityDriverFactory.class);

    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        Class<D> driverInterface = entity.getDriverInterface();
        Class<? extends D> driverClass;
        if (driverInterface.isInterface()) {
            String driverClassName = inferClassName(driverInterface, location);
            try {
                driverClass = (Class<? extends D>) entity.getClass().getClassLoader().loadClass(driverClassName);
            } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
            }
        } else {
            driverClass = driverInterface;
        }

        Constructor<? extends D> constructor = getConstructor(driverClass);
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

    private String inferClassName(Class<? extends EntityDriver> driverInterface, Location location) {
        String driverInterfaceName = driverInterface.getName();
        
        if (location instanceof SshMachineLocation) {
            if (!driverInterfaceName.endsWith("Driver")) {
                throw new RuntimeException(String.format("Driver name [%s] doesn't end with 'Driver'",driverInterfaceName));
            }

            return driverInterfaceName.substring(0, driverInterfaceName.length()-"Driver".length())+"SshDriver";
        } else {
            //TODO: Improve
            throw new RuntimeException("Currently only SshMachineLocation is supported, but location="+location+" for driver +"+driverInterface);
        }
    }
    
    private <D extends EntityDriver> Constructor<D> getConstructor(Class<D> driverClass) {
        for (Constructor<?> constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return (Constructor<D>) constructor;
            }
        }

        //TODO:
        throw new RuntimeException(String.format("Class [%s] has no constructor with 2 arguments",driverClass.getName()));
    }
}
