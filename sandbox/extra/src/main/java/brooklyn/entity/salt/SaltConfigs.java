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
package brooklyn.entity.salt;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.MapConfigKey.MapModifications;
import brooklyn.event.basic.SetConfigKey.SetModifications;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Conveniences for configuring brooklyn Salt entities 
 *
 * @since 0.6.0
 */
@Beta
public class SaltConfigs {

    public static void addToRunList(EntitySpec<?> entity, String...states) {
        for (String state : states) {
            entity.configure(SaltConfig.SALT_RUN_LIST, SetModifications.addItem(state));
        }
    }

    public static void addToRunList(EntityInternal entity, String...states) {
        for (String state : states) {
            entity.setConfig(SaltConfig.SALT_RUN_LIST, SetModifications.addItem(state));
        }
    }

    public static void addToFormuals(EntitySpec<?> entity, String formulaName, String formulaUrl) {
        entity.configure(SaltConfig.SALT_FORMULAS.subKey(formulaName), formulaUrl);
    }

    public static void addToFormulas(EntityInternal entity, String formulaName, String formulaUrl) {
        entity.setConfig(SaltConfig.SALT_FORMULAS.subKey(formulaName), formulaUrl);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntitySpec<?> entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.configure(SaltConfig.SALT_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntityInternal entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.setConfig(SaltConfig.SALT_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntitySpec<?> entity, String rootAttribute, Object value) {
        entity.configure(SaltConfig.SALT_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntityInternal entity, String rootAttribute, Object value) {
        entity.setConfig(SaltConfig.SALT_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }

    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        return Preconditions.checkNotNull(
                Preconditions.checkNotNull(entity, "Entity must be supplied").getConfig(key), 
                "Key "+key+" is required on "+entity);
    }

}
