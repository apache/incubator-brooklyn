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
package brooklyn.entity.software;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters.StopMode;

public class MachineLifecycleEffectorTasksTest {
    public static boolean canStop(StopMode stopMode, boolean isEntityStopped) {
        return MachineLifecycleEffectorTasks.canStop(stopMode, isEntityStopped);
    }
    
    @DataProvider(name = "canStopStates")
    public Object[][] canStopStates() {
        return new Object[][] {
            { StopMode.ALWAYS, true, true },
            { StopMode.ALWAYS, false, true },
            { StopMode.IF_NOT_STOPPED, true, false },
            { StopMode.IF_NOT_STOPPED, false, true },
            { StopMode.NEVER, true, false },
            { StopMode.NEVER, false, false },
        };
    }

    @Test(dataProvider = "canStopStates")
    public void testBasicSonftwareProcessCanStop(StopMode mode, boolean isEntityStopped, boolean expected) {
        boolean canStop = canStop(mode, isEntityStopped);
        assertEquals(canStop, expected);
    }

}
