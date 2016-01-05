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
package org.apache.brooklyn.core.enricher;

import java.util.Map;

import org.apache.brooklyn.api.sensor.EnricherType;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.objs.BrooklynTypeSnapshot;

public class EnricherTypeSnapshot extends BrooklynTypeSnapshot implements EnricherType {
    private static final long serialVersionUID = 4670930188951106009L;
    
    EnricherTypeSnapshot(String name, Map<String, ConfigKey<?>> configKeys) {
        super(name, configKeys);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return (obj instanceof EnricherTypeSnapshot) && super.equals(obj);
    }
}
