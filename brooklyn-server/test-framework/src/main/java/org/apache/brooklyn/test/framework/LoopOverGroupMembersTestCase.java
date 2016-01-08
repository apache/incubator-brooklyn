/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Created by graememiller on 11/12/2015.
 */
@ImplementedBy(value = LoopOverGroupMembersTestCaseImpl.class)
public interface LoopOverGroupMembersTestCase extends TargetableTestComponent {

    /**
     * The test spec that will be run against each member of the group
     */
    @SetFromFlag("testSpec")
    ConfigKey<EntitySpec<? extends TargetableTestComponent>> TEST_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<? extends TargetableTestComponent>>(){},
            "test.spec",
            "Test spec. The test case will create one of these per child");

}
