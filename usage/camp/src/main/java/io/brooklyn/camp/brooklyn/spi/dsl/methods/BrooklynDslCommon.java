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
package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import io.brooklyn.camp.brooklyn.spi.creation.EntitySpecConfiguration;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import io.brooklyn.camp.brooklyn.spi.dsl.DslUtils;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

/** static import functions which can be used in `$brooklyn:xxx` contexts */
public class BrooklynDslCommon {

    public static Object literal(Object expression) {
        return expression;
    }

	public static DslComponent component(String id) {
		return component("global", id);
	}
	
	public static DslComponent component(String scope, String id) {
	    if (!DslComponent.Scope.isValid(scope)) {
	        throw new IllegalArgumentException(scope + " is not a vlaid scope");
	    }
	    return new DslComponent(DslComponent.Scope.fromString(scope), id);
	}
	
	/** returns a DslParsedObject<String> OR a String if it is fully resolved */
    public static Object formatString(final String pattern, final Object ...args) {
        if (DslUtils.resolved(args)) {
            // if all args are resolved, apply the format string now
            return String.format(pattern, args);
        }
        return new FormatString(pattern, args);
    }

    protected static final class FormatString extends BrooklynDslDeferredSupplier<String> {
        private static final long serialVersionUID = -4849297712650560863L;
        private String pattern;
        private Object[] args;

        public FormatString(String pattern, Object ...args) {
            this.pattern = pattern;
            this.args = args;
        }
        @Override
        public Task<String> newTask() {
            return DependentConfiguration.formatString(pattern, args);
        }
        @Override
        public String toString() {
            return "$brooklyn:formatString("+pattern+")";
        }
    }
    
    // TODO: Would be nice to have sensor(String sensorName), which would take the sensor from the entity in question, 
    //       but that would require refactoring of Brooklyn DSL
    // TODO: Should use catalog's classloader, rather than Class.forName; how to get that? Should we return a future?!
    /** returns a Sensor from the given entity type */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object sensor(String clazzName, String sensorName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            Sensor<?> sensor;
            if (Entity.class.isAssignableFrom(clazz)) {
                sensor = new EntityDynamicType((Class<? extends Entity>) clazz).getSensor(sensorName);
            } else {
                // Some non-entity classes (e.g. ServiceRestarter policy) declare sensors that other
                // entities/policies/enrichers may wish to reference.
                Map<String,Sensor<?>> sensors = EntityDynamicType.findSensors((Class)clazz, null);
                sensor = sensors.get(sensorName);
            }
            if (sensor == null)
                throw new IllegalArgumentException("Sensor " + sensorName + " not found on class " + clazzName);
            return sensor;
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
        
    }

    public static EntitySpecConfiguration entitySpec(Map<String, Object> arguments) {
        return new EntitySpecConfiguration(arguments);
    }
}
