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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.PolicyMemento;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicyType;
import org.apache.brooklyn.core.mgmt.rebind.BasicPolicyRebindSupport;
import org.apache.brooklyn.core.objs.AbstractEntityAdjunct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

/**
 * Base {@link Policy} implementation; all policies should extend this or its children
 */
public abstract class AbstractPolicy extends AbstractEntityAdjunct implements Policy, Configurable {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class);

    protected String policyStatus;
    protected AtomicBoolean suspended = new AtomicBoolean(false);

    private final PolicyDynamicType policyType;
    
    public AbstractPolicy() {
        this(Collections.emptyMap());
    }
    
    public AbstractPolicy(Map<?,?> flags) {
        super(flags);
        
        // TODO Don't let `this` reference escape during construction
        policyType = new PolicyDynamicType(this);
        
        if (isLegacyConstruction() && !isLegacyNoConstructionInit()) {
            init();
        }
    }

    @Override
    public PolicyType getPolicyType() {
        return policyType.getSnapshot();
    }

    @Override
    public void suspend() {
        suspended.set(true);
    }

    @Override
    public void resume() {
        suspended.set(false);
    }

    @Override
    public boolean isSuspended() {
        if (suspended==null) {
            // only if accessed during construction in super, e.g. by a call to toString in configure
            return true;
        }
        return suspended.get();
    }

    @Override
    public void destroy(){
        suspend();
        super.destroy();
    }

    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed();
    }

    @Override
    protected void onChanged() {
        // currently changes simply trigger re-persistence; there is no intermediate listener as we do for EntityChangeListener
        if (getManagementContext() != null) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(this);
        }
    }
    
    @Override
    public RebindSupport<PolicyMemento> getRebindSupport() {
        return new BasicPolicyRebindSupport(this);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("name", name)
                .add("running", isRunning())
                .toString();
    }
}
