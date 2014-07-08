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
package io.brooklyn.camp.dto;

import java.util.Map;

import brooklyn.util.collections.MutableMap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableMap;

public class DtoCustomAttributes extends DtoBase {

    private Map<String,Object> customAttributes = new MutableMap<String, Object>();

    protected DtoCustomAttributes() {}
    
    public DtoCustomAttributes(Map<String,?> customAttributes) {
        this.customAttributes = customAttributes==null ? ImmutableMap.<String, Object>of() : ImmutableMap.copyOf(customAttributes);
    }
    
    @JsonIgnore
    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    // --- json ---
    
    @JsonInclude(Include.NON_EMPTY)
    @JsonAnyGetter
    private Map<String,Object> any() {
        return customAttributes;
    }
    @JsonAnySetter
    private void set(String name, Object value) {
        customAttributes.put(name, value);
    }

    // --- building ---

    protected void newInstanceCustomAttributes(Map<String,?> customAttributes) {
        if (customAttributes!=null)
            this.customAttributes.putAll(customAttributes);
    }
    
}
