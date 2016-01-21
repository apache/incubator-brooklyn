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
package org.apache.brooklyn.api.objs;

import java.io.Serializable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;

/** A wrapper around a {@link ConfigKey} which will be added to an {@link Entity},
 * providing additional information for rendering in a UI */
public interface SpecParameter<T> extends Serializable {
    /** Short name, to be used in UI */
    String getLabel();
    /** Whether visible by default in UI, not all inputs may be visible at once */
    boolean isPinned();
    /** All config key info for this spec parameter;
     * this is the config key which is added to the defined type */
    ConfigKey<T> getConfigKey();
    /** An optional sensor which may also be added to the defined type */
    @Nullable AttributeSensor<?> getSensor();

}
