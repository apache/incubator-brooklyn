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
package brooklyn.policy.os;

import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.policy.PolicySpec;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class AdvertiseWinrmLoginPolicyTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testAdvertisesMachineLoginDetails() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(PolicySpec.create(AdvertiseWinrmLoginPolicy.class)));
        
        WinRmMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(WinRmMachineLocation.class)
                .configure("address", "1.2.3.4")
                .configure("user", "myuser")
                .configure("port", 5678)
                .configure("password", "mypassword"));
        app.start(ImmutableList.of(machine));
        
        String expected = "myuser : mypassword @ 1.2.3.4:5678";

        EntityTestUtils.assertAttributeEqualsEventually(entity, AdvertiseWinrmLoginPolicy.VM_USER_CREDENTIALS, expected);
    }
}
