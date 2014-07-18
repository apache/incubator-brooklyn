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
package io.brooklyn.camp.spi.pdp;

import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.yaml.Yamls;

import com.google.common.collect.ImmutableMap;

public class ServiceCharacteristic {

    String name;
    String description;
    String characteristicType;
    
    Map<String,Object> customAttributes;
    
    public static ServiceCharacteristic of(Map<String, Object> req) {
        Map<String,Object> attrs = MutableMap.copyOf(req);
        
        ServiceCharacteristic result = new ServiceCharacteristic();
        result.name = (String) attrs.remove("name");
        result.description = (String) attrs.remove("description");
        result.characteristicType = (String) Yamls.removeMultinameAttribute(attrs, "characteristicType", "type");
        
        // TODO fulfillment
        
        result.customAttributes = attrs;
        
        return result;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public String getCharacteristicType() {
        return characteristicType;
    }
    
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
