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
package org.apache.brooklyn.api.policy;

import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.Rebindable;
import org.apache.brooklyn.api.mgmt.rebind.mementos.PolicyMemento;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.config.ConfigKey;

import com.google.common.annotations.Beta;

/**
 * Policies implement actions and thus must be suspendable; policies should continue to evaluate their sensors
 * and indicate their desired planned action even if they aren't invoking them
 */
public interface Policy extends EntityAdjunct, Rebindable, Configurable {
    /**
     * A unique id for this policy.
     */
    @Override
    String getId();

    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    @Beta
    PolicyType getPolicyType();

    /**
     * Resume the policy
     */
    void resume();

    /**
     * Suspend the policy
     */
    void suspend();
    
    /**
     * Whether the policy is suspended
     */
    boolean isSuspended();
    
    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code policy.config().set(key, val)}
     */
    @Deprecated
    <T> T setConfig(ConfigKey<T> key, T val);
    
    /**
     * Users are strongly discouraged from calling or overriding this method.
     * It is for internal calls only, relating to persisting/rebinding entities.
     * This method may change (or be removed) in a future release without notice.
     */
    @Override
    @Beta
    RebindSupport<PolicyMemento> getRebindSupport();
    
    @Override
    RelationSupport<Policy> relations();
    
}
