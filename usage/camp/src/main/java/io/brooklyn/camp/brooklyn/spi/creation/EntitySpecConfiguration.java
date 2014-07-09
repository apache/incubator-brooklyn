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
package io.brooklyn.camp.brooklyn.spi.creation;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.proxying.EntitySpec;

import com.google.common.collect.Maps;

/**
 * Captures the {@link EntitySpec} configuration defined in YAML. 
 * 
 * This class does not parse that output; it just stores it.
 */
public class EntitySpecConfiguration {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(EntitySpecConfiguration.class);

    private Map<String, Object> specConfiguration;

    public EntitySpecConfiguration(Map<String, ?> specConfiguration) {
        this.specConfiguration = Maps.newHashMap(checkNotNull(specConfiguration, "specConfiguration"));
    }

    public Map<String, Object> getSpecConfiguration() {
        return specConfiguration;
    }
    
    /**
     * Allows BrooklynComponentTemplateResolver to traverse the configuration and resolve any entity specs
     */
    public void setSpecConfiguration(Map<String, Object> specConfiguration) {
       this.specConfiguration =  specConfiguration;
    }
}
