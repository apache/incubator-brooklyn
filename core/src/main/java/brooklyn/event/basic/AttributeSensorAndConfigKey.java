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
package brooklyn.event.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

/**
* A {@link Sensor} describing an attribute that can be configured with inputs that are used to derive the final value.
* <p>
* The {@link ConfigKey} will have the same name and description as the sensor but not necessarily the same type.
* Conversion to set the sensor value from the config key must be supplied in a subclass.
* <p>
* {@link ConfigToAttributes#apply(EntityLocal, AttributeSensorAndConfigKey)} is useful to set the attribute from the sensor.
*/
public abstract class AttributeSensorAndConfigKey<ConfigType,SensorType> extends BasicAttributeSensor<SensorType> 
        implements ConfigKey.HasConfigKey<ConfigType> {
    private static final long serialVersionUID = -3103809215973264600L;
    private static final Logger log = LoggerFactory.getLogger(AttributeSensorAndConfigKey.class);

    private ConfigKey<ConfigType> configKey;

    public AttributeSensorAndConfigKey(Class<ConfigType> configType, Class<SensorType> sensorType, String name) {
        this(configType, sensorType, name, name, null);
    }
    
    public AttributeSensorAndConfigKey(Class<ConfigType> configType, Class<SensorType> sensorType, String name, String description) {
        this(TypeToken.of(configType), TypeToken.of(sensorType), name, description, null);
    }
    
    public AttributeSensorAndConfigKey(Class<ConfigType> configType, Class<SensorType> sensorType, String name, String description, Object defaultValue) {
        this(TypeToken.of(configType), TypeToken.of(sensorType), name, description, defaultValue);
    }

    public AttributeSensorAndConfigKey(TypeToken<ConfigType> configType, TypeToken<SensorType> sensorType, String name) {
        this(configType, sensorType, name, null);
    }

    public AttributeSensorAndConfigKey(TypeToken<ConfigType> configType, TypeToken<SensorType> sensorType, String name, String description) {
        this(configType, sensorType, name, description, null);
    }

    public AttributeSensorAndConfigKey(TypeToken<ConfigType> configType, TypeToken<SensorType> sensorType, String name, String description, Object defaultValue) {
        super(sensorType, name, description);
        ConfigType defaultValueTyped;
        try {
            defaultValueTyped = TypeCoercions.coerce(defaultValue, configType);
        } catch (Exception e) {
            log.warn("Invalid default value '"+defaultValue+"' for "+name+" (rethrowing: "+e, e);
            throw Exceptions.propagate(e);
        }
        configKey = new BasicConfigKey<ConfigType>(configType, name, description, defaultValueTyped);
    }

    public AttributeSensorAndConfigKey(AttributeSensorAndConfigKey<ConfigType,SensorType> orig, ConfigType defaultValue) {
        super(orig.getTypeToken(), orig.getName(), orig.getDescription());
        configKey = ConfigKeys.newConfigKeyWithDefault(orig.configKey, 
                TypeCoercions.coerce(defaultValue, orig.configKey.getTypeToken()));
    }

    public ConfigKey<ConfigType> getConfigKey() { return configKey; }
    
    /** returns the sensor value for this attribute on the given entity, if present,
     * otherwise works out what the sensor value should be based on the config key's value
     * <p>
     * calls to this may allocate resources (e.g. ports) so should be called only once and 
     * then (if non-null) assigned as the sensor's value
     * <p>
     * <b>(for this reason this method should generally not be invoked by callers except in tests and by the framework,
     * and similarly should not be overridden; implement {@link #convertConfigToSensor(Object, Entity)} instead for single-execution calls.
     * the framework calls this from {@link AbstractEntity#setAttribute(AttributeSensorAndConfigKey)} 
     * typically via {@link ConfigToAttributes#apply(EntityLocal)} e.g. from SoftwareProcessImpl.preStart().)
     * </b> 
     */
    public SensorType getAsSensorValue(Entity e) {
        SensorType sensorValue = e.getAttribute(this);
        if (sensorValue!=null) return sensorValue;
        
        ConfigType v = ((EntityLocal)e).getConfig(this);
        try {
            return convertConfigToSensor(v, e);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot convert config value "+v+" for sensor "+this+": "+t, t);
        }
    }

    /**
     * @see {@link #getAsSensorValue(Entity)}
     * 
     * Differs in that the config value is converted based on just the management context, rather
     * than for a specific entity. For example, useful if using {@link BrooklynConfigKeys} in BrooklynWebServer.
     * </b> 
     */
    public SensorType getAsSensorValue(ManagementContext managementContext) {
        ConfigType v = managementContext.getConfig().getConfig(this);
        try {
            return convertConfigToSensor(v, managementContext);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot convert config value "+v+" for sensor "+this+": "+t, t);
        }
    }

    /** converts the given ConfigType value to the corresponding SensorType value, 
     * with respect to the given entity
     * <p>
     * this is invoked after checks whether the entity already has a value for the sensor,
     * and the entity-specific config value is passed for convenience if set, 
     * otherwise the config key default value is passed for convenience
     * <p>
     * this message should be allowed to return null if the conversion cannot be completed at this time */
    protected abstract SensorType convertConfigToSensor(ConfigType value, Entity entity);

    /**
     * @see {@link #convertConfigToSensor(Object, Entity)}
     */
    protected abstract SensorType convertConfigToSensor(ConfigType value, ManagementContext entity);
}
