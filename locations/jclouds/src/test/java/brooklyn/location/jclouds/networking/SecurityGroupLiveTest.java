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
package brooklyn.location.jclouds.networking;

import org.testng.annotations.Test;

@Test(groups = {"Live", "WIP"})
public class SecurityGroupLiveTest {

    public void testCreateEc2WithSecurityGroup() {
        SecurityGroupDefinition sgDef = new SecurityGroupDefinition()
            .allowingInternalPorts(8097, 8098).allowingInternalPortRange(6000, 7999)
            .allowingPublicPort(8099);
        // TODO create machine and test
    }
}
