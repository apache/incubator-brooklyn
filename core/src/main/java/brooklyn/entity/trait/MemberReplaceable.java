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
package brooklyn.entity.trait;

import java.util.NoSuchElementException;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.StopFailedRuntimeException;

public interface MemberReplaceable {

    MethodEffector<String> REPLACE_MEMBER = new MethodEffector<String>(MemberReplaceable.class, "replaceMember");

    /**
     * Replaces the entity with the given ID, if it is a member.
     * <p>
     * First adds a new member, then removes this one. 
     *
     * @param memberId entity id of a member to be replaced
     * @return the id of the new entity
     * @throws NoSuchElementException If entity cannot be resolved, or it is not a member
     * @throws StopFailedRuntimeException If stop failed, after successfully starting replacement
     */
    @Effector(description="Replaces the entity with the given ID, if it is a member; first adds a new member, then removes this one. "+
            "Returns id of the new entity; or throws exception if couldn't be replaced.")
    String replaceMember(@EffectorParam(name="memberId", description="The entity id of a member to be replaced") String memberId);
}
