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

import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.BasicConfigKeyOverwriting;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;


/**
 * Dictionary of {@link ConfigKey} entries.
 */
public class ConfigKeys {

    private static final Logger log = LoggerFactory.getLogger(ConfigKeys.class);
    
    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name) {
        return new BasicConfigKey<T>(type, name);
    }

    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name) {
        return new BasicConfigKey<T>(type, name);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    public static <T> AttributeSensorAndConfigKey<T,T> newSensorAndConfigKey(Class<T> type, String name, String description) {
        return new BasicAttributeSensorAndConfigKey<T>(type, name, description);
    }

    public static <T> AttributeSensorAndConfigKey<T,T> newSensorAndConfigKey(Class<T> type, String name, String description, T defaultValue) {
        return new BasicAttributeSensorAndConfigKey<T>(type, name, description, defaultValue);
    }

    public static <T> AttributeSensorAndConfigKey<T,T> newSensorAndConfigKey(TypeToken<T> type, String name, String description) {
        return new BasicAttributeSensorAndConfigKey<T>(type, name, description);
    }

    public static <T> AttributeSensorAndConfigKey<T,T> newSensorAndConfigKey(TypeToken<T> type, String name, String description, T defaultValue) {
        return new BasicAttributeSensorAndConfigKey<T>(type, name, description, defaultValue);
    }

    public static AttributeSensorAndConfigKey<String,String> newStringSensorAndConfigKey(String name, String description) {
        return new BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey(name, description);
    }

    public static AttributeSensorAndConfigKey<String,String> newStringSensorAndConfigKey(String name, String description, String defaultValue) {
        return new BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey(name, description, defaultValue);
    }

    public static AttributeSensorAndConfigKey<Integer,Integer> newIntegerSensorAndConfigKey(String name, String description) {
        return new BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey(name, description);
    }

    public static AttributeSensorAndConfigKey<Integer,Integer> newIntegerSensorAndConfigKey(String name, String description, Integer defaultValue) {
        return new BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey(name, description, defaultValue);
    }

    public static PortAttributeSensorAndConfigKey newPortSensorAndConfigKey(String name, String description) {
        return new PortAttributeSensorAndConfigKey(name, description);
    }

    public static PortAttributeSensorAndConfigKey newPortSensorAndConfigKey(String name, String description, Object defaultValue) {
        return new PortAttributeSensorAndConfigKey(name, description, defaultValue);
    }

    /** Infers the type from the default value */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> ConfigKey<T> newConfigKey(String name, String description, @Nonnull T defaultValue) {
        return new BasicConfigKey<T>((Class)Preconditions.checkNotNull(defaultValue, 
                "Type must be exlicit for ConfigKey if defaultValue is null").getClass(), 
                name, description, defaultValue);
    }

    public static <T> BasicConfigKey.Builder<T> builder(Class<T> type) {
        return BasicConfigKey.builder(type);
    }

    public static <T> BasicConfigKey.Builder<T> builder(TypeToken<T> type) {
        return BasicConfigKey.builder(type);
    }

    // ---- extensions to keys
    
    public static <T> ConfigKey<T> newConfigKeyWithDefault(ConfigKey<T> parent, T defaultValue) {
        return new BasicConfigKeyOverwriting<T>(parent, defaultValue);
    }

    public static <T> ConfigKey<T> newConfigKeyWithDefault(ConfigKey<T> parent, String newDescription, T defaultValue) {
        return new BasicConfigKeyOverwriting<T>(parent, newDescription, defaultValue);
    }

    public static <T> ConfigKey<T> newConfigKeyRenamed(String newName, ConfigKey<T> key) {
        return new BasicConfigKey<T>(key.getTypeToken(), newName, key.getDescription(), key.getDefaultValue());
    }

    public static <T> ConfigKey<T> newConfigKeyWithPrefix(String prefix, ConfigKey<T> key) {
        return newConfigKeyRenamed(prefix+key.getName(), key);
    }

    /** converts the name of the key from one case-strategy (e.g. lowerCamel) to andother (e.g. lower-hyphen) */
    public static <T> ConfigKey<T> convert(ConfigKey<T> key, CaseFormat inputCaseStrategy, CaseFormat outputCaseStrategy) {
        return newConfigKeyRenamed(inputCaseStrategy.to(outputCaseStrategy, key.getName()), key);
    }

    // ---- typed keys

    public static ConfigKey<String> newStringConfigKey(String name) {
        return newConfigKey(String.class, name);
    }

    public static ConfigKey<String> newStringConfigKey(String name, String description) {
        return newConfigKey(String.class, name, description);
    }
    
    public static ConfigKey<String> newStringConfigKey(String name, String description, String defaultValue) {
        return newConfigKey(String.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Integer> newIntegerConfigKey(String name) {
        return newConfigKey(Integer.class, name);
    }

    public static ConfigKey<Integer> newIntegerConfigKey(String name, String description) {
        return newConfigKey(Integer.class, name, description);
    }
    
    public static ConfigKey<Integer> newIntegerConfigKey(String name, String description, Integer defaultValue) {
        return newConfigKey(Integer.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Long> newLongConfigKey(String name) {
        return newConfigKey(Long.class, name);
    }

    public static ConfigKey<Long> newLongConfigKey(String name, String description) {
        return newConfigKey(Long.class, name, description);
    }
    
    public static ConfigKey<Long> newLongConfigKey(String name, String description, Long defaultValue) {
        return newConfigKey(Long.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Double> newDoubleConfigKey(String name) {
        return newConfigKey(Double.class, name);
    }

    public static ConfigKey<Double> newDoubleConfigKey(String name, String description) {
        return newConfigKey(Double.class, name, description);
    }
    
    public static ConfigKey<Double> newDoubleConfigKey(String name, String description, Double defaultValue) {
        return newConfigKey(Double.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Boolean> newBooleanConfigKey(String name) {
        return newConfigKey(Boolean.class, name);
    }

    public static ConfigKey<Boolean> newBooleanConfigKey(String name, String description) {
        return newConfigKey(Boolean.class, name, description);
    }
    
    public static ConfigKey<Boolean> newBooleanConfigKey(String name, String description, Boolean defaultValue) {
        return newConfigKey(Boolean.class, name, description, defaultValue);
    }
    
    public static class DynamicKeys {

        // TODO see below
//        public static final ConfigKey<String> TYPE = ConfigKeys.newStringConfigKey("type");
        public static final ConfigKey<String> NAME = ConfigKeys.newStringConfigKey("name");
        public static final ConfigKey<String> DESCRIPTION = ConfigKeys.newStringConfigKey("description");
        public static final ConfigKey<Object> DEFAULT_VALUE = ConfigKeys.newConfigKey(Object.class, "defaultValue");
        
        public static ConfigKey<?> newInstance(ConfigBag keyDefs) {
            // TODO dynamic typing - see TYPE key commented out above
            String typeName = Strings.toString(keyDefs.getStringKey("type"));
            if (Strings.isNonBlank(typeName))
                log.warn("Setting 'type' is not currently supported for dynamic config keys; ignoring in definition of "+keyDefs);
            
            Class<Object> type = Object.class;
            String name = keyDefs.get(NAME);
            String description = keyDefs.get(DESCRIPTION);
            Object defaultValue = keyDefs.get(DEFAULT_VALUE);
            return newConfigKey(type, name, description, defaultValue);
        }
        
        /** creates a new {@link ConfigKey} given a map describing it */
        public static ConfigKey<?> newInstance(Map<?,?> keyDefs) {
            return newInstance(ConfigBag.newInstance(keyDefs));
        }
        
        /** creates a new {@link ConfigKey} given a name (e.g. as a key in a larger map) and a map of other definition attributes */
        public static ConfigKey<?> newNamedInstance(String name, Map<?,?> keyDefs) {
            ConfigBag defs = ConfigBag.newInstance(keyDefs);
            String oldName = defs.put(NAME, name);
            if (oldName!=null && !oldName.equals(name))
                log.warn("Dynamic key '"+oldName+"' being overridden as key '"+name+"' in "+keyDefs);
            return newInstance(defs);
        }

    }

}
