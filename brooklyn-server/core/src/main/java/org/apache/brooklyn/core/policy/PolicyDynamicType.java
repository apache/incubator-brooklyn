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
package org.apache.brooklyn.core.policy;

import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicyType;
import org.apache.brooklyn.core.objs.BrooklynDynamicType;

public class PolicyDynamicType extends BrooklynDynamicType<Policy, AbstractPolicy> {

    public PolicyDynamicType(Class<? extends Policy> type) {
        super(type);
    }
    
    public PolicyDynamicType(AbstractPolicy policy) {
        super(policy);
    }
    
    public PolicyType getSnapshot() {
        return (PolicyType) super.getSnapshot();
    }

    @Override
    protected PolicyTypeSnapshot newSnapshot() {
        return new PolicyTypeSnapshot(name, value(configKeys));
    }
}
