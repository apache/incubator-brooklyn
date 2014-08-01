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
package brooklyn.entity.basic;

import brooklyn.policy.Policy;

import com.google.common.base.Objects;

public class PolicyDescriptor {

    private final String id;
    private final String type;
    private final String name;

    public PolicyDescriptor(Policy policy) {
        this.id = policy.getId();
        this.type = policy.getPolicyType().getName();
        this.name = policy.getDisplayName();
    }
    public String getId() {
        return id;
    }
    
    public String getPolicyType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PolicyDescriptor)) {
            return false;
        }
        PolicyDescriptor o = (PolicyDescriptor) other;
        return Objects.equal(id, o.id) && Objects.equal(type, o.type) && Objects.equal(name, o.name);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("id", id).add("type", type).add("name",  name).omitNullValues().toString();
    }
}
