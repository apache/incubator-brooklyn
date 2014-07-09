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

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;

@SuppressWarnings("rawtypes")
public class SubElementConfigKey<T> extends BasicConfigKey<T> {
    
    private static final long serialVersionUID = -1587240876351450665L;
    
    public final ConfigKey parent;

    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name) {
        this(parent, type, name, name, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description) {
        this(parent, type, name, description, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description, T defaultValue) {
        super(type, name, description, defaultValue);
        this.parent = parent;
    }
    
    @Override
    public T extractValue(Map vals, ExecutionContext exec) {
        return super.extractValue(vals, exec);
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        return super.isSet(vals);
    }
}
