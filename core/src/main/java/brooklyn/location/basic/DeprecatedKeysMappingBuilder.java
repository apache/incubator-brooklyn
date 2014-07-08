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
package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;

import brooklyn.config.ConfigKey;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;

/**
* @deprecated since 0.6; for use only in converting deprecated flags; will be deleted in future version.
*/
public class DeprecatedKeysMappingBuilder {
    private final ImmutableMap.Builder<String,String> builder = new ImmutableMap.Builder<String,String>();
    private final Logger logger;
    
    public DeprecatedKeysMappingBuilder(Logger logger) {
        this.logger = logger;
    }

    public DeprecatedKeysMappingBuilder camelToHyphen(ConfigKey<?> key) {
        return camelToHyphen(key.getName());
    }
    
    public DeprecatedKeysMappingBuilder camelToHyphen(String key) {
        String hyphen = toHyphen(key);
        if (key.equals(hyphen)) {
            logger.warn("Invalid attempt to convert camel-case key {} to deprecated hyphen-case: both the same", hyphen);
        } else {
            builder.put(hyphen, key);
        }
        return this;
    }
    
    public DeprecatedKeysMappingBuilder putAll(Map<String,String> vals) {
        builder.putAll(vals);
        return this;
    }

    public Map<String,String> build() {
        return builder.build();
    }
    
    private String toHyphen(String word) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, word);
    }
}
