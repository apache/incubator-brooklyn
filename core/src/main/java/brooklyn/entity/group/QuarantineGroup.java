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
package brooklyn.entity.group;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(QuarantineGroupImpl.class)
public interface QuarantineGroup extends AbstractGroup {

    ConfigKey<Boolean> MEMBER_DELEGATE_CHILDREN = ConfigKeys.newConfigKeyWithDefault(AbstractGroup.MEMBER_DELEGATE_CHILDREN, Boolean.TRUE);

    @Effector(description="Removes all members of the quarantined group, unmanaging them")
    void expungeMembers(
            @EffectorParam(name="firstStop", description="Whether to first call stop() on those members that are stoppable") boolean stopFirst);
}
