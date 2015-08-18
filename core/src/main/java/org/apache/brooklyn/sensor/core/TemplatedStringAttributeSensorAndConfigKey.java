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
package org.apache.brooklyn.sensor.core;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.entity.core.EntityInternal;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * A {@link ConfigKey} which takes a freemarker-templated string,
 * and whose value is converted to a sensor by processing the template 
 * with access to config and methods on the entity where it is set.
 */
public class TemplatedStringAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<String> {
    private static final long serialVersionUID = 4680651022807491321L;
    
    public static final Logger LOG = LoggerFactory.getLogger(TemplatedStringAttributeSensorAndConfigKey.class);

    public TemplatedStringAttributeSensorAndConfigKey(String name) {
        this(name, name, null);
    }
    public TemplatedStringAttributeSensorAndConfigKey(String name, String description) {
        this(name, description, null);
    }
    public TemplatedStringAttributeSensorAndConfigKey(String name, String description, String defaultValue) {
        super(String.class, name, description, defaultValue);
    }
    public TemplatedStringAttributeSensorAndConfigKey(TemplatedStringAttributeSensorAndConfigKey orig, String defaultValue) {
        super(orig, defaultValue);
    }
    
    @Override
    protected String convertConfigToSensor(String value, Entity entity) {
        if (value == null) return null;
        return TemplateProcessor.processTemplateContents(value, (EntityInternal)entity, ImmutableMap.<String,Object>of());
    }
    
    @Override
    protected String convertConfigToSensor(String value, ManagementContext managementContext) {
        if (value == null) return null;
        return TemplateProcessor.processTemplateContents(value, (ManagementContextInternal)managementContext, ImmutableMap.<String,Object>of());
    }
}
