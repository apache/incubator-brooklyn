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
package brooklyn.entity.rebind;

import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

public class BasicPolicyRebindSupport extends AbstractBrooklynObjectRebindSupport<PolicyMemento> {

    private final AbstractPolicy policy;
    
    public BasicPolicyRebindSupport(AbstractPolicy policy) {
        super(policy);
        this.policy = policy;
    }
    
    @Override
    protected void addConfig(RebindContext rebindContext, PolicyMemento memento) {
        // TODO entity does config-lookup differently; the memento contains the config keys.
        // BasicEntityMemento.postDeserialize uses the injectTypeClass to call EntityTypes.getDefinedConfigKeys(clazz)
        // 
        // Note that the flags may have been set in the constructor; but some policies have no-arg constructors
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(policy, configBag);
        FlagUtils.setAllConfigKeys(policy, configBag, false);
    }

    @Override
    protected void addCustoms(RebindContext rebindContext, PolicyMemento memento) {
        // no-op
    }
}
