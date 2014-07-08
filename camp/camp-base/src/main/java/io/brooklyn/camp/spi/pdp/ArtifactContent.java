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

import com.google.common.collect.ImmutableMap;

public class ArtifactContent {

    String href;
    Map<String,Object> customAttributes;
    
    public static ArtifactContent of(Object spec) {
        if (spec==null) return null;
        
        ArtifactContent result = new ArtifactContent();
        if (spec instanceof String) {
            result.href = (String)spec;
        } else if (spec instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String,Object> attrs = MutableMap.copyOf( (Map<String,Object>) spec );
            result.href = (String) attrs.remove("href");
            result.customAttributes = attrs;            
        } else {
            throw new IllegalArgumentException("artifact content should be map or string, not "+spec.getClass());
        }
        
        return result;
    }

    public String getHref() {
        return href;
    }
    
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
