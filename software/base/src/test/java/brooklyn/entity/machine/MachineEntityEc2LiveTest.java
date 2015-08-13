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
package brooklyn.entity.machine;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.location.Location;
import brooklyn.test.Asserts;

public class MachineEntityEc2LiveTest extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        final MachineEntity server = app.createAndManageChild(EntitySpec.create(MachineEntity.class));
        
        app.start(ImmutableList.of(loc));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(MachineEntity.UPTIME));
                assertNotNull(server.getAttribute(MachineEntity.LOAD_AVERAGE));
                assertNotNull(server.getAttribute(MachineEntity.CPU_USAGE));
                assertNotNull(server.getAttribute(MachineEntity.FREE_MEMORY));
                assertNotNull(server.getAttribute(MachineEntity.TOTAL_MEMORY));
                assertNotNull(server.getAttribute(MachineEntity.USED_MEMORY));
            }});
        
        String result = server.execCommand("MY_ENV=myval && echo start $MY_ENV");
        assertTrue(result.contains("start myval"), "result="+result);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
}
